package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.EgressBootstrapResult;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.service.TenantService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantEgressRoutingServiceTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String TENANT = "tenant1";

  @Mock ApplicationManagerService appManagerService;
  @Mock TenantEntitlementService tenantEntitlementService;
  @Mock EgressRoutingLookup egressRoutingLookup;
  @Mock ModuleProperties moduleProperties;
  @Mock TenantService tenantService;

  private TenantEgressRoutingService service;

  @BeforeEach
  void setUp() {
    // lenient: the already-disabled short-circuit returns before isModuleActive() reads the module id
    lenient().when(moduleProperties.getId()).thenReturn(MODULE_ID);
    service = new TenantEgressRoutingService(appManagerService, tenantEntitlementService, egressRoutingLookup,
      moduleProperties);
  }

  @Test
  void refreshTenant_moduleActive_loadsAndUpdates() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    var done = service.refreshTenant(TENANT);

    assertSucceeded(done);
    verify(egressRoutingLookup).updateTenantRoutes(eq(TENANT), any());
    verify(egressRoutingLookup, never()).removeTenantRoutes(TENANT);
  }

  @Test
  void refreshTenant_doRefreshThrowsSynchronously_settlesAndDoesNotWedgeChain() {
    // First refresh: getAllTenantEntitlements throws synchronously (not a failed future), so doRefreshTenant
    // throws before returning a Future. The refresh's promise must still settle (failed) so the per-tenant chain
    // tail is not left unsettled; otherwise every later refresh for this tenant would wedge forever.
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenThrow(new RuntimeException("synchronous boom"))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    var first = service.refreshTenant(TENANT);
    var second = service.refreshTenant(TENANT);

    Assertions.assertThat(first.failed()).isTrue();
    assertSucceeded(second);
    verify(egressRoutingLookup).updateTenantRoutes(eq(TENANT), any());
  }

  @Test
  void refreshTenant_moduleInactive_removesTable() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(emptyList()))));

    var done = service.refreshTenant(TENANT);

    assertSucceeded(done);
    verify(egressRoutingLookup).removeTenantRoutes(TENANT);
    verify(appManagerService, never()).getModuleBootstrapEgress(any());
  }

  @Test
  void refreshTenant_entitleThenRevoke_lastWins() {
    var slowEntitle = Promise.<List<Entitlement>>promise();
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(slowEntitle.future())
      .thenReturn(succeededFuture(List.of(entitlement(emptyList()))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    var entitle = service.refreshTenant(TENANT);
    var revoke = service.refreshTenant(TENANT);

    slowEntitle.complete(List.of(entitlement(List.of(MODULE_ID))));

    assertSucceeded(entitle);
    assertSucceeded(revoke);

    var inOrder = org.mockito.Mockito.inOrder(egressRoutingLookup);
    inOrder.verify(egressRoutingLookup).updateTenantRoutes(eq(TENANT), any());
    inOrder.verify(egressRoutingLookup).removeTenantRoutes(TENANT);
  }

  @Test
  void init_bootstrapNetworkError_propagatesFailure() {
    service.setTenantService(tenantService);
    when(tenantService.getEnabledTenants()).thenReturn(succeededFuture(Set.of(TENANT)));
    when(tenantService.isEnabled(TENANT)).thenReturn(true);
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(Future.failedFuture(new RuntimeException("connection reset")));

    var done = service.init();

    Assertions.assertThat(done.failed()).isTrue();
    Assertions.assertThat(done.cause()).hasMessageContaining("connection reset");
    verify(egressRoutingLookup, never()).updateTenantRoutes(eq(TENANT), any());
    verify(egressRoutingLookup, never()).removeTenantRoutes(TENANT);
  }

  @Test
  void init_bootstrap404_startsWithoutFailing() {
    service.setTenantService(tenantService);
    when(tenantService.getEnabledTenants()).thenReturn(succeededFuture(Set.of(TENANT)));
    when(tenantService.isEnabled(TENANT)).thenReturn(true);
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.empty()));

    var done = service.init();

    assertSucceeded(done);
    verify(egressRoutingLookup, never()).updateTenantRoutes(eq(TENANT), any());
  }

  @Test
  void refreshTenant_egressFoundFalse_removesTable() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(notFound())));

    var done = service.refreshTenant(TENANT);

    assertSucceeded(done);
    verify(egressRoutingLookup).removeTenantRoutes(TENANT);
    verify(egressRoutingLookup, never()).updateTenantRoutes(eq(TENANT), any());
  }

  @Test
  void refreshTenant_foundTrueButNullBootstrap_removesTableWithoutNpe() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    var foundWithoutBootstrap = new EgressBootstrapResult();
    foundWithoutBootstrap.setFound(true);
    foundWithoutBootstrap.setBootstrap(null);
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(foundWithoutBootstrap)));

    var done = service.refreshTenant(TENANT);

    assertSucceeded(done);
    verify(egressRoutingLookup).removeTenantRoutes(TENANT);
    verify(egressRoutingLookup, never()).updateTenantRoutes(eq(TENANT), any());
  }

  @Test
  void refreshTenant_applicationScopeUnchanged_skipsBootstrapReload() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    assertSucceeded(service.refreshTenant(TENANT));
    assertSucceeded(service.refreshTenant(TENANT));

    verify(appManagerService, times(1)).getModuleBootstrapEgress(any());
    verify(egressRoutingLookup, times(1)).updateTenantRoutes(eq(TENANT), any());
  }

  @Test
  void refreshTenant_tenantAlreadyDisabled_removesTableWithoutQueryingMte() {
    // #8: a REVOKE disables the tenant locally before the refresh runs; the table must be dropped without consulting
    // MTE, so a not-yet-propagated "still active" entitlement read cannot keep a stale egress table alive.
    service.setTenantService(tenantService);
    when(tenantService.isEnabled(TENANT)).thenReturn(false);

    var done = service.refreshTenant(TENANT);

    assertSucceeded(done);
    verify(egressRoutingLookup).removeTenantRoutes(TENANT);
    verify(tenantEntitlementService, never()).getAllTenantEntitlements(eq(TENANT), anyBoolean());
    verify(appManagerService, never()).getModuleBootstrapEgress(any());
  }

  @Test
  void refreshTenant_tenantDisabledDuringRefresh_failSafeRemovesTable() {
    // enabled when the refresh starts (short-circuit not taken), then disabled (REVOKE) while the egress bootstrap
    // call is in flight; the in-flight refresh fails and the onFailure fail-safe still drops the now-stale table.
    service.setTenantService(tenantService);
    when(tenantService.isEnabled(TENANT)).thenReturn(true, false);
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(Future.failedFuture(new RuntimeException("bootstrap unavailable")));

    var done = service.refreshTenant(TENANT);

    Assertions.assertThat(done.failed()).isTrue();
    verify(egressRoutingLookup).removeTenantRoutes(TENANT);
  }

  @Test
  void refreshTenant_enabledTenantRefreshFails_keepsExistingTable() {
    service.setTenantService(tenantService);
    when(tenantService.isEnabled(TENANT)).thenReturn(true);
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(Future.failedFuture(new RuntimeException("bootstrap blip")));

    var done = service.refreshTenant(TENANT);

    Assertions.assertThat(done.failed()).isTrue();
    verify(egressRoutingLookup, never()).removeTenantRoutes(TENANT);
  }

  @Test
  void onDiscovery_trackedModule_forcesReloadEvenIfScopeUnchanged() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    assertSucceeded(service.refreshTenant(TENANT));

    service.onDiscovery("mod-bar-1.0.0");

    verify(appManagerService, times(2)).getModuleBootstrapEgress(any());
    verify(egressRoutingLookup, times(2)).updateTenantRoutes(eq(TENANT), any());
  }

  @Test
  void onDiscovery_untrackedModule_doesNotRefresh() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    assertSucceeded(service.refreshTenant(TENANT));

    service.onDiscovery("mod-unrelated-9.9.9");

    verify(appManagerService, times(1)).getModuleBootstrapEgress(any());
  }

  @Test
  void onDiscovery_tenantRefreshInFlight_forcesRefreshAndIsNotDropped() {
    // #7: a discovery arriving while a tenant's FIRST refresh is in flight (metadata not yet written) must not be
    // dropped. The tenant has an in-flight chain entry, so onDiscovery force-refreshes it once the first completes.
    var pendingBootstrap = Promise.<Optional<EgressBootstrapResult>>promise();
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(pendingBootstrap.future())
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    var first = service.refreshTenant(TENANT);   // in flight: bootstrap pending, metadata not yet written
    service.onDiscovery("mod-bar-1.0.0");          // arrives mid-refresh -> must queue a forced refresh, not be lost
    pendingBootstrap.complete(Optional.of(found(List.of(provider()))));

    assertSucceeded(first);
    verify(appManagerService, times(2)).getModuleBootstrapEgress(any());
  }

  @Test
  void refreshTenant_newRefreshAfterDeactivation_isSerializedNotParallel() {
    // Reproduces the removeTenant chain race (#5). A deactivating refresh must not prune the per-tenant chain while
    // another refresh is in flight: a subsequent refresh that arrives afterwards must serialize behind the in-flight
    // one, not spin up a parallel chain. We keep refresh #2 in flight (pending bootstrap) and then issue refresh #3.
    var pendingEntitlements = Promise.<List<Entitlement>>promise();   // refresh #1: resolves to inactive (deactivate)
    var pendingBootstrap = Promise.<Optional<EgressBootstrapResult>>promise();  // refresh #2: kept in flight
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(pendingEntitlements.future())
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(pendingBootstrap.future())
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    var first = service.refreshTenant(TENANT);    // #1: pending on entitlements
    service.refreshTenant(TENANT);                // #2: queued behind #1

    // #1 resolves to inactive -> removeTenant runs; #2 then starts and stays in flight (bootstrap pending)
    pendingEntitlements.complete(List.of(entitlement(emptyList())));
    Assertions.assertThat(first.succeeded()).isTrue();

    // #3 arrives while #2 is still in flight; it must chain behind #2, not run in parallel on a fresh chain
    var third = service.refreshTenant(TENANT);

    Assertions.assertThat(third.isComplete())
      .as("third refresh must serialize behind the in-flight refresh, not run on a parallel chain")
      .isFalse();
    verify(appManagerService, times(1)).getModuleBootstrapEgress(any());
  }

  @Test
  void onEntitlementsChanged_reconcilesEachEnabledTenant_healingMissedStartupLoads() {
    // #6: the entitlements event (also fired by TenantService's scheduled reload) must reconcile egress for every
    // enabled tenant, healing a tenant whose startup egress load was lost to a transient blip (init() recovers).
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement(List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider())))));

    service.onEntitlementsChanged(EntitlementsEvent.of(Set.of(TENANT)));

    verify(egressRoutingLookup).updateTenantRoutes(eq(TENANT), any());
  }

  private static EgressBootstrapResult notFound() {
    var result = new EgressBootstrapResult();
    result.setFound(false);
    return result;
  }

  private static void assertSucceeded(Future<Void> future) {
    org.assertj.core.api.Assertions.assertThat(future.succeeded()).isTrue();
  }

  private static Entitlement entitlement(List<String> modules) {
    return Entitlement.of("app-1.0.0", "t-id", modules);
  }

  private static EgressBootstrapResult found(List<ModuleBootstrapDiscovery> required) {
    var bootstrap = new ModuleBootstrap();
    bootstrap.setRequiredModules(required);
    var result = new EgressBootstrapResult();
    result.setFound(true);
    result.setBootstrap(bootstrap);
    return result;
  }

  private static ModuleBootstrapDiscovery provider() {
    var discovery = new ModuleBootstrapDiscovery();
    discovery.setModuleId("mod-bar-1.0.0");
    return discovery;
  }
}
