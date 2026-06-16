package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toSet;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
  private final Map<String, AtomicReference<Future<Void>>> tenantChains = new ConcurrentHashMap<>();

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
    var ref = tenantChains.computeIfAbsent(tenant, t -> new AtomicReference<>(succeededFuture()));
    var promise = Promise.<Void>promise();
    var previous = ref.getAndSet(promise.future());
    // Run doRefreshTenant inside Future.future so a synchronous throw (e.g. an unexpected programming error
    // before the first Future is returned) is captured as a failed future and still settles this promise.
    // Otherwise the unsettled promise would stay installed as the chain tail forever, silently wedging every
    // subsequent refresh for this tenant.
    previous.onComplete(ignored ->
      Future.<Void>future(p -> doRefreshTenant(tenant, force).onComplete(p)).onComplete(promise));
    return promise.future();
  }

  /**
   * Refreshes only tenants whose tracked provider set contains the discovered module. A discovery event changes a
   * provider's location, not the tenant's application scope, so the refresh is forced to bypass the unchanged-scope
   * short-circuit and actually re-fetch the egress bootstrap (otherwise the new location would never be picked up).
   */
  public void onDiscovery(String moduleId) {
    if (metadata.isEmpty()) {
      return;
    }
    var affected = metadata.entrySet().stream()
      .filter(entry -> entry.getValue().moduleIds().contains(moduleId))
      .map(Entry::getKey)
      .toList();
    if (affected.isEmpty()) {
      log.debug("Discovery for {} affects no tracked tenant egress scope; no refresh", moduleId);
      return;
    }
    log.info("Discovery for {} refreshing tenant egress scopes: {}", moduleId, affected);
    affected.forEach(tenant -> refreshTenant(tenant, true));
  }

  private Future<Void> doRefreshTenant(String tenant, boolean force) {
    return tenantEntitlementService.getAllTenantEntitlements(tenant, true).compose(entitlements -> {
      if (!isModuleActive(entitlements)) {
        removeTenant(tenant);
        log.info("Module no longer active for tenant [{}]; removed egress route table", tenant);
        return succeededFuture();
      }
      var sortedApps = applicationScope(entitlements);
      var existing = metadata.get(tenant);
      if (!force && existing != null && existing.applicationScope().equals(sortedApps)) {
        log.debug("Tenant [{}] application scope unchanged; skipping egress refresh", tenant);
        return succeededFuture();
      }
      return loadTenantEgress(tenant, sortedApps);
    }).onFailure(error -> {
      log.warn("Egress refresh failed for tenant [{}]: {}", tenant, error.getMessage());
      // Fail-safe: if the tenant has been disabled (e.g. a REVOKE) but the entitlement re-query failed, drop the
      // stale egress table so outbound calls fall back to the gateway rather than routing to now-unentitled modules.
      if (tenantService != null && !tenantService.isEnabled(tenant)) {
        log.info("Tenant [{}] disabled and egress refresh failed; removing egress route table (fail-safe)", tenant);
        removeTenant(tenant);
      }
    });
  }

  /**
   * Authoritatively drops a tenant's egress state: its route table, tracked metadata, and serialization chain.
   * Pruning the chain keeps {@link #tenantChains} from accumulating entries for tenants that churn over the lifetime
   * of the instance; a later event for the tenant recreates a fresh chain. Per-tenant events are processed in order,
   * so this does not break last-event-wins serialization.
   */
  private void removeTenant(String tenant) {
    egressRoutingLookup.removeTenantRoutes(tenant);
    metadata.remove(tenant);
    tenantChains.remove(tenant);
  }

  private Future<Void> loadTenantEgress(String tenant, List<String> sortedApps) {
    return appManagerService.getModuleBootstrapEgress(sortedApps).map(optional -> {
      if (optional.isEmpty()) {
        log.warn("POST /bootstrap unavailable; scoped egress inactive for tenant [{}]", tenant);
        return null;
      }
      applyTenantResult(tenant, sortedApps, optional.get());
      return null;
    });
  }

  private void applyTenantResult(String tenant, List<String> sortedApps, EgressBootstrapResult result) {
    if (result == null || !result.isFound() || result.getBootstrap() == null) {
      log.warn("Egress unavailable for tenant [{}] (module not in scope or empty bootstrap); ensuring no egress table",
        tenant);
      removeTenant(tenant);
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
