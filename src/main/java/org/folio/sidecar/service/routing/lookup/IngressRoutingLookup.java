package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.lookup;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.routing.ModuleBootstrapListener;

@Log4j2
@Named("ingressLookup")
@ApplicationScoped
public class IngressRoutingLookup implements RoutingLookup, ModuleBootstrapListener {

  private static final Pattern UUID_PATTERN = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final String UUID_PLACEHOLDER = "{id}";

  private Map<String, List<ScRoutingEntry>> ingressRequestCache = new HashMap<>();

  private final Cache<String, Optional<ScRoutingEntry>> routeLookupCache = Caffeine.newBuilder()
    .maximumSize(5000)
    .expireAfterAccess(Duration.ofMinutes(10))
    .build();

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for ingress request: method [{}], uri [{}]", request::method, dumpUri(rc));

    var method = request.method() != null ? request.method().name() : "";
    var moduleId = request.getHeader(OkapiHeaders.MODULE_ID);
    var normalizedPath = normalizePath(path);
    var cacheKey = normalizedPath + "#" + method + "#" + (moduleId != null ? moduleId : "");

    var entry = routeLookupCache.get(cacheKey, k -> lookup(request, path, ingressRequestCache, false));

    log.debug("Ingress entry found: {}", entry);

    return succeededFuture(entry);
  }

  /**
   * Normalizes path by replacing UUIDs with a placeholder to improve cache hit rate.
   * For example: /inventory/instances/abc-123-def-456 → /inventory/instances/{id}
   */
  static String normalizePath(String path) {
    if (path == null) {
      return "";
    }
    return UUID_PATTERN.matcher(path).replaceAll(UUID_PLACEHOLDER);
  }

  @Override
  public void onModuleBootstrap(ModuleBootstrapDiscovery moduleBootstrap, ChangeType changeType) {
    log.info("{} module ingress routes", changeType == INIT ? "Initializing" : "Updating");

    ingressRequestCache = getRoutes(moduleBootstrap);
    routeLookupCache.invalidateAll();

    log.info("Ingress routes {}: count = {}", () -> changeType == INIT ? "initialized" : "updated",
      () -> calculateRoutes(ingressRequestCache));
  }
}
