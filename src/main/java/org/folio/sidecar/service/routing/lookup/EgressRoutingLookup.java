package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getCollectedRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.lookup;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.routing.ModuleBootstrapListener;
import org.folio.sidecar.utils.RoutingUtils;

/**
 * Finds the egress route of a request.
 *
 * <p>With tenant-scoped routing disabled, one global table serves all requests. With it enabled, the table is
 * chosen by the tenant of the request. Tenants entitled to the same applications share one
 * {@link EgressRouteTable}, so a table is held once per set of applications, not once per tenant.</p>
 *
 * <p>Reads are lock-free. Writes to the tenant-scoped tables are rare and synchronized, so binding, rebuilding
 * and cleanup do not interleave.</p>
 */
@Log4j2
@Named("egressLookup")
@ApplicationScoped
public class EgressRoutingLookup implements RoutingLookup, ModuleBootstrapListener {

  @ConfigProperty(name = "routing.tenant-scoped.enabled") boolean tenantScoped;

  private Map<String, List<ScRoutingEntry>> egressRequestCache = new HashMap<>();

  /**
   * Tenant name to application-set key. Tenants entitled to the same applications share one table.
   */
  private final Map<String, String> tenantTableKeys = new ConcurrentHashMap<>();

  /**
   * Application-set key to egress table.
   */
  private final Map<String, EgressRouteTable> egressTables = new ConcurrentHashMap<>();

  /**
   * Finds the egress route of the request. In tenant-scoped mode the table is chosen by the request's tenant; a
   * missing or unknown tenant, or a tenant without a table yet, gives no match. Otherwise the global table is
   * used.
   *
   * @param path - request path
   * @param rc - routing context of the request
   */
  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for egress request: method [{}], uri [{}]", request::method, dumpUri(rc));

    var routes = tenantScoped ? getTenantRoutes(RoutingUtils.getTenant(rc)) : egressRequestCache;
    var entry = routes == null ? Optional.<ScRoutingEntry>empty() : lookup(request, path, routes, true);

    log.debug("Egress entry found: {}", entry);
    return succeededFuture(entry);
  }

  @Override
  public void onRequiredModulesBootstrap(List<ModuleBootstrapDiscovery> requiredModulesBootstrap,
    ChangeType changeType) {
    log.info("{} module egress routes", changeType == INIT ? "Initializing" : "Updating");

    egressRequestCache = getCollectedRoutes(requiredModulesBootstrap);

    log.info("Egress routes {}: count = {}", () -> changeType == INIT ? "initialized" : "updated",
      () -> calculateRoutes(egressRequestCache));
  }

  /**
   * Returns the table of an application set, or {@code null} if no tenant uses the set.
   *
   * @param key - application-set key: the sorted application ids joined with commas
   */
  public EgressRouteTable getTable(String key) {
    return egressTables.get(key);
  }

  /**
   * Binds a tenant to the table of its application set. The table is stored only if the set has none yet, so a table
   * that was updated in place is never overwritten by an older instance of the same set. Tables that no tenant uses
   * any more, for example the set the tenant was bound to before, are dropped.
   *
   * @param tenant - tenant name
   * @param key - application-set key
   * @param table - the table to store if the set has none
   */
  public synchronized void bindTenant(String tenant, String key, EgressRouteTable table) {
    var bound = egressTables.computeIfAbsent(key, k -> table);
    tenantTableKeys.put(tenant, key);
    removeUnusedTables();
    log.info("Tenant egress routes bound: tenant = {}, applications = [{}], count = {}",
      () -> tenant, () -> key, () -> calculateRoutes(bound.routes()));
  }

  /**
   * Replaces a rebuilt table, unless no tenant uses its application set any more.
   *
   * @param key - application-set key
   * @param table - the rebuilt table
   */
  public synchronized void replaceTable(String key, EgressRouteTable table) {
    egressTables.computeIfPresent(key, (k, old) -> table);
  }

  /**
   * Unbinds the tenant and drops its table if no other tenant uses the same application set.
   *
   * @param tenant - tenant name
   */
  public synchronized void removeTenant(String tenant) {
    tenantTableKeys.remove(tenant);
    removeUnusedTables();
    log.info("Tenant egress routes removed: tenant = {}", tenant);
  }

  /**
   * Tells whether any table has routes to the module.
   *
   * @param moduleId - module id
   */
  public boolean routesToModule(String moduleId) {
    return egressTables.values().stream().anyMatch(table -> table.containsModule(moduleId));
  }

  /**
   * Returns the keys of the tables that satisfy the condition.
   *
   * @param condition - the test applied to each table
   */
  public List<String> findTableKeys(Predicate<EgressRouteTable> condition) {
    return egressTables.entrySet().stream()
      .filter(entry -> condition.test(entry.getValue()))
      .map(Map.Entry::getKey)
      .toList();
  }

  /**
   * Points the routes of the module to a new location in every table that has them. Each table is replaced by a
   * copy, so a request never sees a half-updated table.
   *
   * @param moduleId - module id
   * @param location - new location of the module
   */
  public synchronized void updateModuleLocation(String moduleId, String location) {
    egressTables.replaceAll((key, table) ->
      table.containsModule(moduleId) ? table.withLocation(moduleId, location) : table);
    log.info("Egress module location updated: moduleId = {}, location = {}", moduleId, location);
  }

  /**
   * Removes the routes of the module from every table that has them, after its discovery was deleted. The tables
   * remember the module id, see {@link EgressRouteTable#withoutModule(String)}.
   *
   * @param moduleId - module id
   */
  public synchronized void removeModule(String moduleId) {
    egressTables.replaceAll((key, table) ->
      table.containsModule(moduleId) ? table.withoutModule(moduleId) : table);
    log.info("Egress module routes removed after discovery deletion: moduleId = {}", moduleId);
  }

  /**
   * Returns the routes of the tenant's table, or {@code null} if the tenant is unknown or has no table yet.
   *
   * @param tenant - tenant name, can be {@code null}
   */
  private Map<String, List<ScRoutingEntry>> getTenantRoutes(String tenant) {
    var key = tenant == null ? null : tenantTableKeys.get(tenant);
    var table = key == null ? null : egressTables.get(key);
    return table == null ? null : table.routes();
  }

  /**
   * Drops the tables whose application set no tenant is bound to.
   */
  private void removeUnusedTables() {
    var usedKeys = Set.copyOf(tenantTableKeys.values());
    egressTables.keySet().removeIf(key -> !usedKeys.contains(key));
  }
}
