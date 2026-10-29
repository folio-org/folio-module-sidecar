package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.fromCompletionStage;
import static io.vertx.core.Future.succeededFuture;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.faulttolerance.api.TypedGuard;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.ws.rs.WebApplicationException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.routing.lookup.EgressRouteTable;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;

/**
 * Builds and maintains the egress routes of tenant-scoped routing.
 *
 * <p>Routes are held in tables, one per set of entitled applications. Tenants entitled to the same applications
 * share one table and so one bootstrap call. The service:</p>
 * <ul>
 *   <li>builds the table of every newly enabled tenant and drops revoked tenants
 *   ({@link #onEntitlementsChanged(EntitlementsEvent)});</li>
 *   <li>re-resolves a tenant after an upgrade ({@link #refreshTenant(String)});</li>
 *   <li>applies discovery changes of provider modules to the tables in place ({@link #onDiscovery(String)}).</li>
 * </ul>
 *
 * <p>Table builds and rebuilds call mgr-applications and mgr-tenant-entitlements through a bulkhead that allows
 * {@code routing.tenant-scoped.bootstrap-concurrency} calls at once. Event-driven rebuilds are delayed by a random
 * time of up to {@code routing.tenant-scoped.refresh-max-stagger}, so that sidecars do not all call
 * mgr-applications at the same moment.</p>
 *
 * <p>Every public method does nothing when tenant-scoped routing is disabled.</p>
 */
@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class EgressBootstrapService {

  /**
   * Size of the bulkhead queue. It is far above the number of tenants, so the queue does not overflow in
   * practice. A task that finds the queue full fails like any other failed build.
   */
  private static final int BOOTSTRAP_QUEUE_SIZE = 10_000;

  @ConfigProperty(name = "routing.tenant-scoped.enabled") boolean tenantScoped;
  @ConfigProperty(name = "routing.tenant-scoped.bootstrap-concurrency") int bootstrapConcurrency;
  @ConfigProperty(name = "routing.tenant-scoped.refresh-max-stagger") Duration refreshMaxStagger;

  private final ApplicationManagerService appManagerService;
  private final TenantEntitlementService tenantEntitlementService;
  private final EgressRoutingLookup egressRoutingLookup;
  private final Vertx vertx;
  /**
   * Tenants that have a table bound.
   */
  private final Set<String> tenants = new ConcurrentHashSet<>();
  /**
   * Bootstrap calls in flight, by application-set key, so concurrent builds of one set share one call.
   */
  private final Map<String, Future<EgressRouteTable>> tableLoads = new ConcurrentHashMap<>();
  /**
   * Tenants whose build is queued or running.
   */
  private final Set<String> buildingTenants = new ConcurrentHashSet<>();
  /**
   * Limits concurrent table builds. It is created once for the service's lifetime: a guard built per call
   * would limit nothing.
   */
  private TypedGuard<CompletionStage<Void>> bootstrapGuard;

  /**
   * Creates the bulkhead with the configured concurrency. A value below 1 fails the startup.
   */
  @PostConstruct
  void init() {
    bootstrapGuard = TypedGuard.create(new TypeLiteral<CompletionStage<Void>>() {})
      .withBulkhead().limit(bootstrapConcurrency).queueSize(BOOTSTRAP_QUEUE_SIZE).done()
      .build();
  }

  /**
   * Reconciles the tables with the set of tenants the module is enabled for: builds a table for each tenant that
   * has none, and drops tenants that are no longer enabled. A tenant whose build is already queued or running is
   * not queued again, and a failed build is retried by the next event.
   *
   * @param event - the enabled tenants of the module
   */
  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void onEntitlementsChanged(EntitlementsEvent event) {
    if (!tenantScoped) {
      return;
    }
    var target = event.getTenants() == null ? Set.<String>of() : event.getTenants();
    // buildingTenants::add is false for a tenant that is already queued or running
    target.stream()
      .filter(tenant -> !tenants.contains(tenant))
      .filter(buildingTenants::add)
      .forEach(this::submitBuild);
    Set.copyOf(tenants).stream().filter(tenant -> !target.contains(tenant)).forEach(this::dropTenant);
  }

  /**
   * Re-resolves the tenant's application set after an UPGRADE. A table that already exists for the new set is
   * reused. Unlike {@link #onEntitlementsChanged(EntitlementsEvent)}, it always rebuilds, even if a build for the
   * tenant is running.
   *
   * @param tenant - tenant name
   */
  public void refreshTenant(String tenant) {
    if (tenantScoped) {
      withStagger(() -> buildEgress(tenant));
    }
  }

  /**
   * Applies a discovery change of another module to the egress tables without asking mgr-applications for a new
   * bootstrap.
   *
   * <ul>
   *   <li>The module is in a table: load its discovery once and update the location in place. A 404 means the
   *   discovery was deleted, so the module's routes are removed.</li>
   *   <li>The module was removed from a table earlier because its discovery was deleted: rebuild only those tables
   *   (see {@link EgressRouteTable#withoutModule(String)}).</li>
   *   <li>Any other module: ignore the event. Tables are built from the tenant's entitled applications, and
   *   entitlement requires discovery for every module, so this module is not one of the tenant's providers.</li>
   * </ul>
   *
   * @param moduleId - id of the module whose discovery changed
   */
  public void onDiscovery(String moduleId) {
    if (!tenantScoped) {
      return;
    }
    // different application sets can be in different states for the same module, so both checks run
    egressRoutingLookup.findTableKeys(table -> table.isRemoved(moduleId)).forEach(this::rebuildTable);
    if (egressRoutingLookup.routesToModule(moduleId)) {
      updateModuleLocation(moduleId);
    }
  }

  /**
   * Queues the build of a tenant's egress table. The tenant stays in {@code buildingTenants} until the build ends,
   * whether it succeeds or fails, so a repeated entitlements event (the entitlements reload can publish it again) does
   * not queue it twice, and a failed build is retried by the next event.
   */
  private void submitBuild(String tenant) {
    log.info("Submitting build of tenant's egress table: {}", tenant);
    bounded(() -> buildEgress(tenant))
      .onComplete(result -> buildingTenants.remove(tenant));
  }

  /**
   * Reads the tenant's entitlements, binds the tenant to the table of its application set, and marks the tenant
   * as built. A failure is logged and leaves the tenant's previous table in place.
   *
   * @param tenant - tenant name
   * @return future completed when the tenant is bound, or failed with the cause
   */
  private Future<Void> buildEgress(String tenant) {
    return tenantEntitlementService.getTenantEntitlements(tenant, false)
      .map(EgressBootstrapService::toApplicationIds)
      .compose(applicationIds -> bindTenantTable(tenant, applicationIds))
      .onSuccess(v -> tenants.add(tenant))
      .onFailure(error -> log.warn("Failed to build egress for tenant {}: {}", tenant, error.getMessage()));
  }

  /**
   * Binds the tenant to the table of the application set. An existing table is reused without any call; an empty
   * set gets an empty table; otherwise the table is loaded with {@link #loadTable(List)}.
   *
   * @param tenant - tenant name
   * @param applicationIds - sorted, distinct ids of the applications the tenant is entitled to
   */
  private Future<Void> bindTenantTable(String tenant, List<String> applicationIds) {
    var key = toTableKey(applicationIds);
    var existing = egressRoutingLookup.getTable(key);
    if (existing != null) {
      log.debug("Reusing existing egress table for tenant: tenant = {}, applications = [{}]", tenant, key);
      egressRoutingLookup.bindTenant(tenant, key, existing);
      return succeededFuture();
    }
    return loadTable(applicationIds)
      .onSuccess(table -> egressRoutingLookup.bindTenant(tenant, key, table))
      .mapEmpty();
  }

  /**
   * Loads the table for an application set. Concurrent requests for the same set share one bootstrap call, so N
   * tenants with the same applications cause one POST, not N.
   */
  private Future<EgressRouteTable> loadTable(List<String> applicationIds) {
    if (applicationIds.isEmpty()) {
      return succeededFuture(EgressRouteTable.empty());
    }

    var key = toTableKey(applicationIds);
    var promise = Promise.<EgressRouteTable>promise();
    var inFlight = tableLoads.putIfAbsent(key, promise.future());
    if (inFlight != null) {
      log.debug("Joining in-flight egress table load: applications = [{}]", key);
      return inFlight;
    }

    fetchTable(applicationIds).onComplete(result -> {
      tableLoads.remove(key);
      promise.handle(result);
    });
    return promise.future();
  }

  /**
   * Calls mgr-applications for the egress bootstrap of the application set and builds a table from the response.
   *
   * @param applicationIds - sorted, distinct application ids
   */
  private Future<EgressRouteTable> fetchTable(List<String> applicationIds) {
    return appManagerService.getEgressBootstrap(applicationIds)
      .map(bootstrap -> EgressRouteTable.of(bootstrap.getRequiredModules()))
      .onSuccess(table -> log.info("Egress table loaded: applications = [{}]",
        () -> toTableKey(applicationIds)));
  }

  /**
   * Loads the discovery of a module that is in a table and updates its location in place. A failure is handled
   * by {@link #onDiscoveryLookupFailure(String, Throwable)}.
   *
   * @param moduleId - id of the module whose discovery changed
   */
  private void updateModuleLocation(String moduleId) {
    appManagerService.lookupModuleDiscovery(moduleId)
      .onSuccess(discovery -> egressRoutingLookup.updateModuleLocation(moduleId, discovery.getLocation()))
      .onFailure(error -> onDiscoveryLookupFailure(moduleId, error));
  }

  /**
   * A 404 means the module's discovery was deleted, so its routes are removed. Any other failure rebuilds the
   * tables that contain the module from a fresh bootstrap.
   *
   * @param moduleId - id of the module whose discovery was loaded
   * @param error - the failure of the discovery lookup
   */
  private void onDiscoveryLookupFailure(String moduleId, Throwable error) {
    if (isNotFound(error)) {
      egressRoutingLookup.removeModule(moduleId);
      log.info("Module discovery not found, removing module routes: moduleId = {}", moduleId);
      return;
    }
    log.warn("Failed to load module discovery, rebuilding affected egress tables: moduleId = {}, error = {}",
      moduleId, error.getMessage());
    egressRoutingLookup.findTableKeys(table -> table.containsModule(moduleId)).forEach(this::rebuildTable);
  }

  /**
   * Rebuilds the table of one application set from a fresh bootstrap, and does not join a load already in flight,
   * because that load may have started before the change that triggered this rebuild.
   */
  private void rebuildTable(String key) {
    withStagger(() -> bounded(
      () -> fetchTable(fromTableKey(key))
        .onSuccess(table -> egressRoutingLookup.replaceTable(key, table))
        .onFailure(error -> log.warn("Failed to rebuild egress table: applications = [{}], error = {}",
          key, error.getMessage()))
        .mapEmpty()
    ));
  }

  /**
   * Runs the task with at most {@code bootstrapConcurrency} in flight, and the rest wait in the guard's queue. A task
   * that throws, a failed future, and a full queue (BulkheadException) all give a failed future and free the slot.
   */
  private Future<Void> bounded(Supplier<Future<Void>> task) {
    return fromCompletionStage(bootstrapGuard.get(() -> task.get().toCompletionStage()));
  }

  /**
   * Runs the action after a random delay of up to {@code routing.tenant-scoped.refresh-max-stagger}, or at once
   * if the stagger is zero.
   *
   * @param action - the action to run
   */
  private void withStagger(Runnable action) {
    var maxStaggerMillis = refreshMaxStagger.toMillis();
    if (maxStaggerMillis <= 0) {
      action.run();
      return;
    }
    vertx.setTimer(1 + ThreadLocalRandom.current().nextLong(maxStaggerMillis), id -> action.run());
  }

  /**
   * Unbinds the tenant. Its table is dropped if no other tenant uses the same application set.
   *
   * @param tenant - tenant name
   */
  private void dropTenant(String tenant) {
    egressRoutingLookup.removeTenant(tenant);
    tenants.remove(tenant);
  }

  /**
   * Tells whether the error is an HTTP 404 response parsed by {@code JsonConverter}.
   *
   * @param error - the failure of a call
   */
  private static boolean isNotFound(Throwable error) {
    return error instanceof WebApplicationException wae && wae.getResponse().getStatus() == NOT_FOUND.getStatusCode();
  }

  /**
   * Returns the distinct, sorted application ids of the entitlements. Sorting makes the ids a stable key, so
   * tenants with the same applications get the same table.
   *
   * @param entitlements - entitlements of one tenant
   */
  private static List<String> toApplicationIds(ResultList<Entitlement> entitlements) {
    return entitlements.getRecords().stream()
      .map(Entitlement::getApplicationId)
      .filter(Objects::nonNull)
      .distinct()
      .sorted()
      .toList();
  }

  /**
   * Joins the sorted application ids with commas. Application ids never contain a comma, so the key can be
   * split back with {@link #fromTableKey(String)}.
   *
   * @param sortedApplicationIds - sorted application ids
   */
  private static String toTableKey(List<String> sortedApplicationIds) {
    return String.join(",", sortedApplicationIds);
  }

  /**
   * Splits a key made by {@link #toTableKey(List)} back into application ids; an empty key is an empty list.
   *
   * @param key - application-set key
   */
  private static List<String> fromTableKey(String key) {
    return StringUtils.isEmpty(key) ? List.of() : List.of(key.split(","));
  }
}
