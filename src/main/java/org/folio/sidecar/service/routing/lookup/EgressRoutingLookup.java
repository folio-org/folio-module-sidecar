package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getCollectedRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.lookup;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.ModuleBootstrapListener;

@Log4j2
@Named("egressLookup")
@ApplicationScoped
@RequiredArgsConstructor
public class EgressRoutingLookup implements RoutingLookup, ModuleBootstrapListener {

  /**
   * Per-application routing cache: applicationId → (pathPrefix → list of routing entries).
   */
  private final Map<String, Map<String, List<ScRoutingEntry>>> cachePerApplication = new ConcurrentHashMap<>();

  private final TenantService tenantService;

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for egress request: method [{}], uri [{}]", request::method, dumpUri(rc));

    var tenant = request.getHeader(OkapiHeaders.TENANT);
    var applicationIds = tenantService.getApplicationIds(tenant);
    var entry = findEntryInApplicationCaches(path, request, applicationIds);

    if (entry.isEmpty()) {
      log.debug("No egress entry found for path [{}]", path);
    }
    return succeededFuture(entry);
  }

  private Optional<ScRoutingEntry> findEntryInApplicationCaches(String path,
    HttpServerRequest request, Set<String> applicationIds) {
    // Sorted for deterministic selection when tenant has multiple apps with overlapping routes
    for (var appId : applicationIds.stream().sorted().toList()) {
      var appCache = cachePerApplication.get(appId);
      if (appCache != null) {
        var entry = lookup(request, path, appCache, true);
        if (entry.isPresent()) {
          log.debug("Egress route selected: applicationId={} interface={} module={} -> {}",
            appId, entry.get().getInterfaceId(), entry.get().getModuleId(), entry.get().getLocation());
          return entry;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Populates (or replaces) the routing cache for the given applicationId.
   *
   * @param applicationId the application whose modules are being bootstrapped
   * @param modules       list of required-module discovery entries for that application
   */
  public void onApplicationBootstrap(String applicationId, List<ModuleBootstrapDiscovery> modules) {
    log.info("Initializing egress routes for application: {}", applicationId);

    var routes = getCollectedRoutes(modules);
    cachePerApplication.put(applicationId, routes);

    log.info("Egress routes initialized for application {}: count = {}", applicationId, calculateRoutes(routes));
  }

  /**
   * Removes the routing cache entry for the given applicationId (e.g. after revocation).
   *
   * @param applicationId the application being revoked
   */
  public void onApplicationRevoked(String applicationId) {
    log.info("Removing egress routes for revoked application: {}", applicationId);
    cachePerApplication.remove(applicationId);
  }

  /**
   * Backward-compatible entry point: distributes required modules into per-application caches grouped by
   * {@code applicationId}.  Called from {@link org.folio.sidecar.service.routing.RoutingService} during startup and
   * on discovery updates until Task 3.6 migrates the call site to {@link #onApplicationBootstrap}.
   *
   * @param requiredModulesBootstrap list of required-module discovery entries (may span multiple applications)
   * @param changeType               INIT or UPDATE (informational only)
   */
  @Override
  public void onRequiredModulesBootstrap(List<ModuleBootstrapDiscovery> requiredModulesBootstrap,
    ChangeType changeType) {
    log.info("{} module egress routes", changeType == INIT ? "Initializing" : "Updating");

    if (requiredModulesBootstrap == null || requiredModulesBootstrap.isEmpty()) {
      return;
    }

    var byApp = new HashMap<String, List<ModuleBootstrapDiscovery>>();
    for (var module : requiredModulesBootstrap) {
      byApp.computeIfAbsent(module.getApplicationId(), k -> new ArrayList<>()).add(module);
    }
    byApp.forEach(this::onApplicationBootstrap);
  }
}
