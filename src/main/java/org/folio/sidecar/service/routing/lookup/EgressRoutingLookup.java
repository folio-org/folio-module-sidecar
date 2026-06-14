package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getCollectedRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.lookup;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;
import static org.folio.sidecar.utils.RoutingUtils.getTenant;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.model.ScRoutingEntry;

@Log4j2
@Named("egressLookup")
@ApplicationScoped
public class EgressRoutingLookup implements RoutingLookup {

  private final Map<String, Map<String, List<ScRoutingEntry>>> tenantEgressCaches = new ConcurrentHashMap<>();

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    var tenant = getTenant(rc);
    log.debug("Searching egress routing entries: tenant [{}], method [{}], uri [{}]",
      () -> tenant, request::method, dumpUri(rc));

    var cache = tenant == null ? null : tenantEgressCaches.get(tenant);
    if (cache == null) {
      log.debug("No scoped egress table for tenant [{}]; forwarding unmatched egress downstream", tenant);
      return succeededFuture(Optional.empty());
    }

    var entry = lookup(request, path, cache, true);
    log.debug("Egress entry found: {}", entry);
    return succeededFuture(entry);
  }

  /**
   * Atomically replaces a tenant's egress route table built from the supplied required modules.
   * Building the table before the put makes the swap atomic; other tenants are untouched.
   *
   * @param tenant target tenant identifier
   * @param requiredModules list of module bootstrap discoveries for the tenant
   */
  public void updateTenantRoutes(String tenant, List<ModuleBootstrapDiscovery> requiredModules) {
    var table = getCollectedRoutes(requiredModules);
    tenantEgressCaches.put(tenant, table);
    log.info("Egress routes updated for tenant [{}]: count = {}", () -> tenant, () -> calculateRoutes(table));
  }

  /**
   * Removes the egress route table for the given tenant.
   *
   * @param tenant target tenant identifier
   */
  public void removeTenantRoutes(String tenant) {
    if (tenantEgressCaches.remove(tenant) != null) {
      log.info("Egress routes removed for tenant [{}]", tenant);
    }
  }

  /**
   * Returns {@code true} if a route table exists for the given tenant.
   *
   * @param tenant target tenant identifier
   * @return {@code true} if routes are present for the tenant
   */
  public boolean hasTenant(String tenant) {
    return tenantEgressCaches.containsKey(tenant);
  }
}
