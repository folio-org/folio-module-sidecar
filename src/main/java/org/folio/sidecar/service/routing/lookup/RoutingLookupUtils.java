package org.folio.sidecar.service.routing.lookup;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.sidecar.utils.RoutingUtils.MULTIPLE_INTERFACE_TYPE;

import io.vertx.core.http.HttpServerRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.utils.CollectionUtils;

@Log4j2
@UtilityClass
class RoutingLookupUtils {

  static Optional<ScRoutingEntry> lookup(HttpServerRequest request, String path,
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

  static Map<String, List<ScRoutingEntry>> getCollectedRoutes(List<ModuleBootstrapDiscovery> modules) {
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

  static Map<String, List<ScRoutingEntry>> getRoutes(ModuleBootstrapDiscovery discovery) {
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

  static long calculateRoutes(Map<String, List<ScRoutingEntry>> requestCache) {
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

  private static boolean matchModuleIdForMultipleInterface(ScRoutingEntry candidate, HttpServerRequest request,
    boolean isSupportMultipleInterface) {

    var moduleIdHeader = request.getHeader(OkapiHeaders.MODULE_ID);
    var interfaceType = candidate.getInterfaceType();

    if (!isSupportMultipleInterface && !isEmpty(moduleIdHeader)) {
      return !Objects.equals(MULTIPLE_INTERFACE_TYPE, interfaceType)
        || Objects.equals(moduleIdHeader, candidate.getModuleId());
    }

    if (isEmpty(moduleIdHeader)) {
      return !Objects.equals(MULTIPLE_INTERFACE_TYPE, interfaceType);
    } else {
      return Objects.equals(moduleIdHeader, candidate.getModuleId())
        && Objects.equals(MULTIPLE_INTERFACE_TYPE, interfaceType);
    }
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
