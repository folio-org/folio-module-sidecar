package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toSet;

import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.EgressBootstrapResult;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.utils.GenericCompositeFuture;

@Log4j2
@ApplicationScoped
public class TenantEgressRoutingService {

  private final ApplicationManagerService appManagerService;
  private final TenantEntitlementService tenantEntitlementService;
  private final EgressRoutingLookup egressRoutingLookup;
  private final ModuleProperties moduleProperties;

  private final Map<String, TenantEgressMetadata> metadata = new ConcurrentHashMap<>();
  // Per-tenant serialization chain: maps a tenant to the tail Future of its refresh chain. Mutated only via the
  // map's atomic compute/computeIfPresent so a concurrent refresh and a concurrent removeTenant cannot interleave.
  private final Map<String, Future<Void>> tenantChains = new ConcurrentHashMap<>();

  private TenantService tenantService;

  public TenantEgressRoutingService(ApplicationManagerService appManagerService,
    TenantEntitlementService tenantEntitlementService, EgressRoutingLookup egressRoutingLookup,
    ModuleProperties moduleProperties) {
    this.appManagerService = appManagerService;
    this.tenantEntitlementService = tenantEntitlementService;
    this.egressRoutingLookup = egressRoutingLookup;
    this.moduleProperties = moduleProperties;
  }

  @Inject
  void setTenantService(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  /**
   * Loads egress route tables for every active tenant. Fails (propagates) when a reachable bootstrap call errors,
   * so the caller can fail startup; a missing endpoint (Optional.empty) or per-tenant found=false does not fail.
   */
  public Future<Void> init() {
    return tenantService.getEnabledTenants().compose(tenants -> {
      if (tenants.isEmpty()) {
        log.info("No active tenants; static ingress only, no scoped egress route tables");
        return succeededFuture();
      }
      log.info("Loading scoped egress for {} active tenant(s)", tenants.size());
      var loads = tenants.stream().map(this::refreshTenant).toList();
      return GenericCompositeFuture.all(loads).mapEmpty();
    });
  }

  /**
   * Serializes refreshes per tenant via non-blocking Vert.x Future tail-chaining: each refresh waits for the prior
   * refresh of the same tenant to settle before running, guaranteeing the latest event's result is the one retained.
   */
  public Future<Void> refreshTenant(String tenant) {
    return refreshTenant(tenant, false);
  }

  private Future<Void> refreshTenant(String tenant, boolean force) {
    var promise = Promise.<Void>promise();
    var thisRefresh = promise.future();
    // Swap in this refresh as the chain tail atomically and capture the previous tail. ConcurrentHashMap.compute
    // holds the per-key lock, so this append and the prune in removeTenant (computeIfPresent on the same key) are
    // mutually exclusive and can never interleave into parallel chains. The actual chaining runs AFTER compute
    // returns: doing it inside the lambda would recursively mutate the same map (removeTenant runs synchronously
    // when the previous tail is already complete) — which ConcurrentHashMap forbids.
    var previousTail = new AtomicReference<Future<Void>>();
    tenantChains.compute(tenant, (t, tail) -> {
      previousTail.set(tail == null ? Future.succeededFuture() : tail);
      return thisRefresh;
    });
    // Run doRefreshTenant inside Future.future so a synchronous throw (e.g. an unexpected programming error before
    // the first Future is returned) is captured as a failed future and still settles this promise; otherwise the
    // unsettled promise would stay installed as the chain tail forever, wedging every subsequent refresh.
    previousTail.get().onComplete(ignored ->
      Future.<Void>future(p -> doRefreshTenant(tenant, force, thisRefresh).onComplete(p)).onComplete(promise));
    return thisRefresh;
  }

  /**
   * Refreshes tenants whose tracked provider set contains the discovered module, plus any tenant whose first refresh
   * is still in flight (its metadata is not yet written, so a discovery racing that refresh would otherwise be
   * dropped). A discovery event changes a provider's location, not the tenant's application scope, so the refresh is
   * forced to bypass the unchanged-scope short-circuit and actually re-fetch the egress bootstrap (otherwise the new
   * location would never be picked up).
   */
  public void onDiscovery(String moduleId) {
    var affected = new LinkedHashSet<String>();
    // Tenants whose tracked egress scope already includes the discovered module.
    metadata.forEach((tenant, meta) -> {
      if (meta.moduleIds().contains(moduleId)) {
        affected.add(tenant);
      }
    });
    // Tenants whose first refresh is still in flight (metadata not yet written): a discovery racing that refresh
    // would be missed by the check above, so force them too. Scoped to currently-refreshing, not-yet-tracked tenants
    // only; the forced refresh re-fetches the egress bootstrap and picks up the new provider location if in scope.
    tenantChains.forEach((tenant, tail) -> {
      if (!tail.isComplete() && !metadata.containsKey(tenant)) {
        affected.add(tenant);
      }
    });
    if (affected.isEmpty()) {
      log.debug("Discovery for {} affects no tracked tenant egress scope; no refresh", moduleId);
      return;
    }
    log.info("Discovery for {} refreshing tenant egress scopes: {}", moduleId, affected);
    affected.forEach(tenant -> refreshTenant(tenant, true));
  }

  /**
   * Reconciles egress for every currently-enabled tenant whenever the tenant/entitlement set is (re)loaded. The
   * {@link EntitlementsEvent} is published both on the initial load and on {@code TenantService}'s scheduled reload,
   * so this is a periodic self-heal: a tenant whose startup egress load was lost to a transient MTE/AM blip (which
   * {@code SidecarInitializer} recovers to a successful, fallback-routed start) gets its scoped table rebuilt without
   * waiting for a per-tenant entitlement event or a restart. Refreshes serialize per tenant and short-circuit when the
   * application scope is unchanged, so already-loaded tenants incur no extra bootstrap calls.
   */
  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void onEntitlementsChanged(EntitlementsEvent event) {
    var tenants = event == null ? null : event.getTenants();
    if (tenants == null || tenants.isEmpty()) {
      return;
    }
    log.debug("Reconciling tenant egress on entitlements change: tenants = {}", tenants);
    tenants.forEach(this::refreshTenant);
  }

  private Future<Void> doRefreshTenant(String tenant, boolean force, Future<Void> thisRefresh) {
    if (tenantService != null && !tenantService.isEnabled(tenant)) {
      // The tenant has already been disabled locally (e.g. a REVOKE applied synchronously before this refresh ran).
      // Drop the egress table without consulting MTE: a not-yet-propagated entitlement read could still report the
      // module active and wrongly keep routing to now-unentitled modules. Outbound calls fall back to the gateway.
      removeTenant(tenant, thisRefresh);
      log.info("Tenant [{}] disabled; removed egress route table", tenant);
      return succeededFuture();
    }
    return tenantEntitlementService.getAllTenantEntitlements(tenant, true)
      .compose(entitlements -> reconcileEntitlements(tenant, force, thisRefresh, entitlements))
      .onFailure(error -> onRefreshFailure(tenant, thisRefresh, error));
  }

  private Future<Void> reconcileEntitlements(String tenant, boolean force, Future<Void> thisRefresh,
    List<Entitlement> entitlements) {
    if (!isModuleActive(entitlements)) {
      removeTenant(tenant, thisRefresh);
      log.info("Module no longer active for tenant [{}]; removed egress route table", tenant);
      return succeededFuture();
    }
    var sortedApps = applicationScope(entitlements);
    var existing = metadata.get(tenant);
    if (!force && existing != null && existing.applicationScope().equals(sortedApps)) {
      log.debug("Tenant [{}] application scope unchanged; skipping egress refresh", tenant);
      return succeededFuture();
    }
    return loadTenantEgress(tenant, sortedApps, thisRefresh);
  }

  private void onRefreshFailure(String tenant, Future<Void> thisRefresh, Throwable error) {
    log.warn("Egress refresh failed for tenant [{}]: {}", tenant, error.getMessage());
    // Fail-safe: if the tenant has been disabled (e.g. a REVOKE) but the entitlement re-query failed, drop the
    // stale egress table so outbound calls fall back to the gateway rather than routing to now-unentitled modules.
    if (tenantService != null && !tenantService.isEnabled(tenant)) {
      log.info("Tenant [{}] disabled and egress refresh failed; removing egress route table (fail-safe)", tenant);
      removeTenant(tenant, thisRefresh);
    }
  }

  /**
   * Authoritatively drops a tenant's egress state: its route table, tracked metadata, and serialization chain.
   * Pruning the chain keeps {@link #tenantChains} from accumulating entries for tenants that churn over the lifetime
   * of the instance; a later event for the tenant recreates a fresh chain.
   *
   * <p>The chain is pruned only when {@code thisRefresh} is still its tail — i.e. no newer refresh has chained on.
   * The check + removal run inside {@code computeIfPresent}, which holds the map's per-key lock, so it cannot
   * interleave with a concurrent {@link #refreshTenant} append (which runs under the same lock via {@code compute}).
   * Removing unconditionally would let a concurrent refresh recreate a parallel chain, breaking per-tenant
   * serialization; if a newer refresh has already chained on, the entry is left in place for it.</p>
   */
  private void removeTenant(String tenant, Future<Void> thisRefresh) {
    egressRoutingLookup.removeTenantRoutes(tenant);
    metadata.remove(tenant);
    tenantChains.computeIfPresent(tenant, (t, tail) -> tail == thisRefresh ? null : tail);
  }

  private Future<Void> loadTenantEgress(String tenant, List<String> sortedApps, Future<Void> thisRefresh) {
    return appManagerService.getModuleBootstrapEgress(sortedApps)
      .onSuccess(optional -> {
        if (optional.isEmpty()) {
          log.warn("POST /bootstrap unavailable; scoped egress inactive for tenant [{}]", tenant);
        } else {
          applyTenantResult(tenant, sortedApps, optional.get(), thisRefresh);
        }
      })
      .mapEmpty();
  }

  private void applyTenantResult(String tenant, List<String> sortedApps, EgressBootstrapResult result,
    Future<Void> thisRefresh) {
    if (result == null || !result.isFound() || result.getBootstrap() == null) {
      log.warn("Egress unavailable for tenant [{}] (module not in scope or empty bootstrap); ensuring no egress table",
        tenant);
      removeTenant(tenant, thisRefresh);
      return;
    }
    var requiredModules = result.getBootstrap().getRequiredModules();
    if (requiredModules == null) {
      requiredModules = List.of();
    }
    egressRoutingLookup.updateTenantRoutes(tenant, requiredModules);
    var moduleIds = requiredModules.stream().map(ModuleBootstrapDiscovery::getModuleId).collect(toSet());
    metadata.put(tenant, new TenantEgressMetadata(sortedApps, moduleIds));
  }

  private boolean isModuleActive(List<Entitlement> entitlements) {
    var moduleId = moduleProperties.getId();
    return entitlements.stream()
      .anyMatch(e -> e.getModules() != null && e.getModules().contains(moduleId));
  }

  private static List<String> applicationScope(List<Entitlement> entitlements) {
    return entitlements.stream()
      .map(Entitlement::getApplicationId)
      .filter(Objects::nonNull)
      .distinct()
      .sorted()
      .toList();
  }
}
