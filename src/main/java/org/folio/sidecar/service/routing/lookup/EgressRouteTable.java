package org.folio.sidecar.service.routing.lookup;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getCollectedRoutes;
import static org.folio.sidecar.utils.CollectionUtils.toStream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.model.ScRoutingEntry;

/**
 * Egress routes built from one egress bootstrap response. All tenants entitled to the same set of applications share
 * one table.
 *
 * <p>Instances are immutable. A location change or a module removal creates a new table, which replaces the old one
 * in a single map write, so a request never sees a half-updated table.</p>
 *
 * @param routes - routing entries by path prefix, as built by {@link RoutingLookupUtils#getCollectedRoutes(List)}
 * @param moduleIds - ids of the modules this table routes to
 * @param removedModuleIds - ids of modules whose routes were removed because their discovery was deleted. See
 *   {@link #withoutModule(String)} for why these are kept.
 */
public record EgressRouteTable(Map<String, List<ScRoutingEntry>> routes, Set<String> moduleIds,
  Set<String> removedModuleIds) {

  private static final EgressRouteTable EMPTY_EGRESS_ROUTE_TABLE = new EgressRouteTable(Map.of(), Set.of(), Set.of());

  public static EgressRouteTable of(List<ModuleBootstrapDiscovery> requiredModules) {
    var moduleIds = toStream(requiredModules).map(ModuleBootstrapDiscovery::getModuleId).collect(toUnmodifiableSet());
    return new EgressRouteTable(getCollectedRoutes(requiredModules), moduleIds, Set.of());
  }

  public static EgressRouteTable empty() {
    return EMPTY_EGRESS_ROUTE_TABLE;
  }

  public boolean containsModule(String moduleId) {
    return moduleIds.contains(moduleId);
  }

  public boolean isRemoved(String moduleId) {
    return removedModuleIds.contains(moduleId);
  }

  /**
   * Returns a copy of this table where the routes of the given module point to the new location.
   */
  public EgressRouteTable withLocation(String moduleId, String location) {
    var updated = new HashMap<String, List<ScRoutingEntry>>(routes.size());
    routes.forEach((prefix, entries) -> updated.put(prefix, entries.stream()
      .map(entry -> moduleId.equals(entry.getModuleId()) ? relocate(entry, location) : entry)
      .toList()));
    return new EgressRouteTable(Map.copyOf(updated), moduleIds, removedModuleIds);
  }

  /**
   * Returns a copy of this table without the routes of the given module, and records the module id as removed.
   *
   * <p>Why the removed id is kept: discovery events only carry a module id. To avoid a rebuild for every discovery
   * event in the cluster, the sidecar ignores events for modules that are not in any table. That is safe for modules
   * that were never in the table: the table was built from the tenant's entitled applications, and entitlement
   * requires every module of an application to have discovery, so a module missing from the table is not one of the
   * tenant's providers. A module whose discovery was <b>deleted</b> is different: it is one of the tenant's
   * providers, but after its routes are removed it is no longer in {@link #moduleIds}. Without this set, a later
   * discovery <b>re-create</b> for it would be ignored, and its routes would stay missing until the sidecar restarts
   * or the tenant's entitlements change. The set lets the sidecar recognize that event and rebuild only this
   * table.</p>
   */
  public EgressRouteTable withoutModule(String moduleId) {
    var updated = new HashMap<String, List<ScRoutingEntry>>(routes.size());
    routes.forEach((prefix, entries) -> {
      var kept = entries.stream().filter(entry -> !moduleId.equals(entry.getModuleId())).toList();
      if (!kept.isEmpty()) {
        updated.put(prefix, kept);
      }
    });

    var remainingModules = new HashSet<>(moduleIds);
    remainingModules.remove(moduleId);
    var removedModules = new HashSet<>(removedModuleIds);
    removedModules.add(moduleId);
    return new EgressRouteTable(Map.copyOf(updated), Set.copyOf(remainingModules), Set.copyOf(removedModules));
  }

  private static ScRoutingEntry relocate(ScRoutingEntry entry, String location) {
    return ScRoutingEntry.of(entry.getModuleId(), location, entry.getInterfaceId(), entry.getInterfaceType(),
      entry.getRoutingEntry());
  }
}
