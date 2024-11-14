package org.folio.sidecar.service.routing;

import static org.folio.sidecar.model.ScRoutingEntry.gatewayRoutingEntry;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.utils.CollectionUtils;
import org.folio.sidecar.utils.RoutingUtils;
import org.folio.sidecar.utils.StringUtils;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class RequestMatchingService {

  Map<String, List<ScRoutingEntry>> ingressRequestCache = new HashMap<>();
  Map<String, List<ScRoutingEntry>> egressRequestCache = new HashMap<>();

  private final PathProcessor pathProcessor;
  private final SidecarProperties sidecarProperties;
  private final ScRoutingEntry gatewayRoutingEntry;

  /**
   * Injects dependencies from quarkus context and initializes internal caches.
   *
   * @param pathProcessor - request path processor
   * @param sidecarProperties - Sidecar configuration properties
   */
  @Inject
  public RequestMatchingService(PathProcessor pathProcessor, SidecarProperties sidecarProperties) {
    this.pathProcessor = pathProcessor;
    this.sidecarProperties = sidecarProperties;
    this.gatewayRoutingEntry = gatewayRoutingEntry(sidecarProperties.getUnknownRequestsDestination());
  }

  /**
   * Checks if {@link RoutingContext} request can be classified as ingress request and provides {@link ScRoutingEntry}
   * for it.
   *
   * @param rc - {@link RoutingContext} object to analyze
   * @return {@link Optional} of {@link ScRoutingEntry} for ingress request, it will be empty if request cannot be
   *   classified as ingress request
   */
  public Optional<ScRoutingEntry> lookupForIngressRequest(RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for ingress request: method [{}], path [{}]",
      request.method(), request.path());

    var path = pathProcessor.cleanIngressRequestPath(rc.request().path());
    var entry = lookup(request, path, ingressRequestCache, false);
    entry.ifPresent(scRoutingEntry -> RoutingUtils.putScRoutingEntry(rc, scRoutingEntry));

    log.debug("Ingress entry found: {}", entry);

    return entry;
  }

  /**
   * Checks if {@link RoutingContext} request can be classified as egress request and provides {@link ScRoutingEntry}
   * for it.
   *
   * @param rc - {@link RoutingContext} object to analyze
   * @return {@link Optional} of {@link ScRoutingEntry} for egress request, it will be empty if request cannot be
   *   classified as egress request
   */
  public Optional<ScRoutingEntry> lookupForEgressRequest(RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for egress request: method [{}], path [{}]",
      request.method(), request.path());

    var path = pathProcessor.cleanIngressRequestPath(rc.request().path());
    var entry = lookup(request, path, egressRequestCache, true);

    if (sidecarProperties.isForwardUnknownRequests() && entry.isEmpty()) {
      var moduleIdHeader = request.getHeader(OkapiHeaders.MODULE_ID);
      var unknownRequestsDestination = sidecarProperties.getUnknownRequestsDestination();
      log.warn("Egress routing entry was not found for the request's path. Forwarding request to the Gateway: "
          + "moduleId = {}, path = {}, destination = {}, x-okapi-module-id = {}",
        sidecarProperties.getName(), path, unknownRequestsDestination, moduleIdHeader);
      entry = StringUtils.isBlank(moduleIdHeader)
        ? Optional.of(gatewayRoutingEntry)
        : Optional.of(gatewayRoutingEntry(unknownRequestsDestination, moduleIdHeader));
    }

    entry.ifPresent(scRoutingEntry -> RoutingUtils.putScRoutingEntry(rc, scRoutingEntry));

    log.debug("Egress entry found: {}", entry);

    return entry;
  }

  /**
   * Registers given {@link ModuleBootstrap} as known ingress/egress routes.
   *
   * @param bootstrap - {@link ModuleBootstrap} object to analyze
   */
  public void bootstrapModule(ModuleBootstrap bootstrap) {
    log.info("Initializing module routes from bootstrap information");

    ingressRequestCache = getRoutes(bootstrap.getModule());
    egressRequestCache = getCollectedRoutes(bootstrap.getRequiredModules());

    log.info("Routes initialized: ingress routes [count = {}], egress routes [count = {}]",
      () -> calculateRoutes(ingressRequestCache), () -> calculateRoutes(egressRequestCache));
  }

  public void updateIngressRoutes(ModuleBootstrapDiscovery discovery) {
    log.info("Updating module ingress routes");

    ingressRequestCache = getRoutes(discovery);

    log.info("Ingress routes updated [count = {}]", () -> calculateRoutes(ingressRequestCache));
  }

  public void updateEgressRoutes(List<ModuleBootstrapDiscovery> discoveries) {
    log.info("Updating module egress routes");

    egressRequestCache = getCollectedRoutes(discoveries);

    log.info("Egress routes updated [count = {}]", () -> calculateRoutes(egressRequestCache));
  }

  private static Map<String, List<ScRoutingEntry>> getCollectedRoutes(List<ModuleBootstrapDiscovery> modules) {
    if (CollectionUtils.isEmpty(modules)) {
      return Collections.emptyMap();
    }

    var resultMap = new HashMap<String, List<ScRoutingEntry>>();

    for (var module : modules) {
      var routes = getRoutes(module);

      for (var entry : routes.entrySet()) {
        resultMap.computeIfAbsent(entry.getKey(), v -> new ArrayList<>()).addAll(entry.getValue());
      }
    }

    return resultMap;
  }

  private static Map<String, List<ScRoutingEntry>> getRoutes(ModuleBootstrapDiscovery discovery) {
    if (discovery == null) {
      return Collections.emptyMap();
    }

    var moduleId = discovery.getModuleId();
    var map = new HashMap<String, List<ScRoutingEntry>>();

    log.debug("Collecting routes for module: {}", moduleId);

    for (var moduleInterface : discovery.getInterfaces()) {
      var interfaceId = moduleInterface.getId();
      var interfaceType = moduleInterface.getInterfaceType();
      for (var routingEntry : moduleInterface.getEndpoints()) {
        var prefix = getPatternPrefix(routingEntry);
        var list = map.computeIfAbsent(prefix, k -> new ArrayList<>());

        var re = ScRoutingEntry.of(moduleId, discovery.getLocation(), interfaceId, interfaceType, routingEntry);
        list.add(re);

        log.debug("Routing entry added: prefix [{}], entry [{}]", prefix, re);
      }
    }

    return map;
  }

  private static long calculateRoutes(Map<String, List<ScRoutingEntry>> requestCache) {
    return requestCache.values().stream()
      .filter(Objects::nonNull)
      .mapToLong(Collection::size)
      .sum();
  }

  private static String getPatternPrefix(ModuleBootstrapEndpoint endpoint) {
    String pathPattern = endpoint.getPathPattern();
    if (pathPattern == null) {
      return "/"; // anything but pathPattern is legacy, so we don't care about those
    }

    var lastSlash = 0;
    for (var i = 0; i < pathPattern.length(); i++) {
      switch (pathPattern.charAt(i)) {
        case '*', '{':
          return pathPattern.substring(0, lastSlash);
        case '/':
          lastSlash = i + 1;
          break;
        default:
          break;
      }
    }
    return pathPattern;
  }

  private Optional<ScRoutingEntry> lookup(HttpServerRequest request, String path,
    Map<String, List<ScRoutingEntry>> routingEntries, boolean isSupportMultipleInterface) {
    if (CollectionUtils.isEmpty(routingEntries) || path == null) {
      return Optional.empty();
    }

    var tryPath = path;
    var index = tryPath.lastIndexOf('/', tryPath.length() - 2);
    while (index >= 0) {
      var candidateInstances = routingEntries.get(tryPath);
      if (candidateInstances != null) {
        for (var candidate : candidateInstances) {
          if (match(candidate, request, path, isSupportMultipleInterface)) {
            return Optional.of(candidate);
          }
        }
      }

      index = tryPath.lastIndexOf('/', tryPath.length() - 2);
      tryPath = tryPath.substring(0, index + 1);
    }
    return Optional.empty();
  }

  private static boolean matchModuleIdForMultipleInterface(ScRoutingEntry candidate, HttpServerRequest request,
    boolean isSupportMultipleInterface) {
    if (!isSupportMultipleInterface) {
      return true;
    }

    var interfaceType = candidate.getInterfaceType();
    var moduleIdHeader = request.getHeader(OkapiHeaders.MODULE_ID);
    if (!Objects.equals("multiple", interfaceType)) {
      return true;
    }

    return Objects.equals(moduleIdHeader, candidate.getModuleId());
  }

  private static boolean match(ScRoutingEntry candidate, HttpServerRequest request, String uri,
    boolean isSupportMultipleInterface) {
    var requestMethod = request.method();
    var methods = CollectionUtils.toList(candidate.getRoutingEntry().getMethods());
    for (var method : methods) {
      if (requestMethod == null || method.equals("*") || method.equals(requestMethod.name())) {
        return matchUri(candidate.getRoutingEntry(), uri)
          && matchModuleIdForMultipleInterface(candidate, request, isSupportMultipleInterface);
      }
    }
    return false;
  }

  private static boolean matchUri(ModuleBootstrapEndpoint re, String path) {
    var pathPattern = re.getPathPattern();
    if (pathPattern != null) {
      return fastMatch(pathPattern, path);
    }

    return re.getPath() == null || path.startsWith(re.getPath());
  }

  private static boolean fastMatch(String pathPattern, String path) {
    return fastMatch(pathPattern, 0, path, 0, path.length());
  }

  private static boolean fastMatch(String pathPattern, int patternIndex, String path, int uriIndex, int pathLength) {
    while (patternIndex < pathPattern.length()) {
      var patternChar = pathPattern.charAt(patternIndex);
      patternIndex++;
      if (patternChar == '{') {
        while (true) {
          if (pathPattern.charAt(patternIndex) == '}') {
            patternIndex++;
            break;
          }
          patternIndex++;
        }
        var empty = true;
        while (uriIndex < pathLength && path.charAt(uriIndex) != '/') {
          uriIndex++;
          empty = false;
        }
        if (empty) {
          return false;
        }
      } else if (patternChar != '*') {
        if (uriIndex == pathLength || patternChar != path.charAt(uriIndex)) {
          return false;
        }
        uriIndex++;
      } else {
        do {
          if (fastMatch(pathPattern, patternIndex, path, uriIndex, pathLength)) {
            return true;
          }
          uriIndex++;
        } while (uriIndex <= pathLength);
        return false;
      }
    }
    return uriIndex == pathLength;
  }
}
