package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.folio.sidecar.support.TestConstants.APPLICATION_ID;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP_EGRESS;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import jakarta.ws.rs.WebApplicationException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.routing.lookup.EgressRouteTable;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.TestValues;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EgressBootstrapServiceTest {

  private static final String TENANT_B = "tenant-b";
  private static final String APP_A = "app-a-1.0.0";
  private static final String APP_B = "app-b-1.0.0";

  @Mock private ApplicationManagerService appManagerService;
  @Mock private TenantEntitlementService tenantEntitlementService;
  @Mock private EgressRoutingLookup egressRoutingLookup;
  @Mock private Vertx vertx;

  private EgressBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new EgressBootstrapService(appManagerService, tenantEntitlementService, egressRoutingLookup, vertx);
    service.tenantScoped = true;
    service.refreshMaxStagger = Duration.ZERO;
    initGuard(5);
  }

  @Test
  void onEntitlementsChanged_positive_buildsAddedTenant() {
    mockEntitlements(TENANT_NAME, APPLICATION_ID);
    mockBootstrap(APPLICATION_ID);

    service.onEntitlementsChanged(event(TENANT_NAME));

    verify(egressRoutingLookup).bindTenant(eq(TENANT_NAME), eq(APPLICATION_ID), any(EgressRouteTable.class));
  }

  @Test
  void onEntitlementsChanged_positive_dropsRemovedTenant() {
    mockEntitlements(TENANT_NAME, APPLICATION_ID);
    mockBootstrap(APPLICATION_ID);
    service.onEntitlementsChanged(event(TENANT_NAME));

    service.onEntitlementsChanged(EntitlementsEvent.of(Set.of()));

    verify(egressRoutingLookup).removeTenant(TENANT_NAME);
  }

  @Test
  void onEntitlementsChanged_positive_tenantsWithSameApplicationsShareOneBootstrap() {
    mockEntitlements(TENANT_NAME, APPLICATION_ID);
    mockEntitlements(TENANT_B, APPLICATION_ID);
    var bootstrap = Promise.<ModuleBootstrap>promise();
    when(appManagerService.getEgressBootstrap(List.of(APPLICATION_ID))).thenReturn(bootstrap.future());

    service.onEntitlementsChanged(event(TENANT_NAME, TENANT_B));
    bootstrap.complete(MODULE_BOOTSTRAP_EGRESS);

    verify(appManagerService, times(1)).getEgressBootstrap(List.of(APPLICATION_ID));
    verify(egressRoutingLookup).bindTenant(eq(TENANT_NAME), eq(APPLICATION_ID), any(EgressRouteTable.class));
    verify(egressRoutingLookup).bindTenant(eq(TENANT_B), eq(APPLICATION_ID), any(EgressRouteTable.class));
  }

  @Test
  void onEntitlementsChanged_positive_differentApplicationSetsLoadSeparately() {
    mockEntitlements(TENANT_NAME, APP_A);
    mockEntitlements(TENANT_B, APP_B);
    mockBootstrap(APP_A);
    mockBootstrap(APP_B);

    service.onEntitlementsChanged(event(TENANT_NAME, TENANT_B));

    verify(egressRoutingLookup).bindTenant(eq(TENANT_NAME), eq(APP_A), any(EgressRouteTable.class));
    verify(egressRoutingLookup).bindTenant(eq(TENANT_B), eq(APP_B), any(EgressRouteTable.class));
  }

  @Test
  void onEntitlementsChanged_positive_applicationIdsSortedAndDistinct() {
    mockEntitlements(TENANT_NAME, "b", "a", "b");
    mockBootstrap("a", "b");

    service.onEntitlementsChanged(event(TENANT_NAME));

    verify(appManagerService).getEgressBootstrap(List.of("a", "b"));
    verify(egressRoutingLookup).bindTenant(eq(TENANT_NAME), eq("a,b"), any(EgressRouteTable.class));
  }

  @Test
  void onEntitlementsChanged_positive_noApplications_bindsEmptyTableWithoutBootstrap() {
    when(tenantEntitlementService.getTenantEntitlements(TENANT_NAME, false))
      .thenReturn(succeededFuture(ResultList.empty()));

    service.onEntitlementsChanged(event(TENANT_NAME));

    verify(egressRoutingLookup).bindTenant(eq(TENANT_NAME), eq(""), any(EgressRouteTable.class));
    verify(appManagerService, never()).getEgressBootstrap(any());
  }

  @Test
  void onEntitlementsChanged_positive_existingTableReused() {
    var existing = EgressRouteTable.of(MODULE_BOOTSTRAP_EGRESS.getRequiredModules());
    mockEntitlements(TENANT_NAME, APPLICATION_ID);
    when(egressRoutingLookup.getTable(APPLICATION_ID)).thenReturn(existing);

    service.onEntitlementsChanged(event(TENANT_NAME));

    verify(egressRoutingLookup).bindTenant(TENANT_NAME, APPLICATION_ID, existing);
    verify(appManagerService, never()).getEgressBootstrap(any());
  }

  @Test
  void onEntitlementsChanged_positive_repeatedEventWhileBuildQueued_buildsOnce() {
    initGuard(1);
    var firstEntitlements = Promise.<ResultList<Entitlement>>promise();
    when(tenantEntitlementService.getTenantEntitlements(TENANT_NAME, false)).thenReturn(firstEntitlements.future());
    when(tenantEntitlementService.getTenantEntitlements(TENANT_B, false))
      .thenReturn(succeededFuture(ResultList.empty()));
    var tenants = event(TENANT_NAME, TENANT_B);

    service.onEntitlementsChanged(tenants);
    service.onEntitlementsChanged(tenants);
    firstEntitlements.complete(ResultList.empty());

    verify(tenantEntitlementService, times(1)).getTenantEntitlements(TENANT_NAME, false);
    verify(tenantEntitlementService, times(1)).getTenantEntitlements(TENANT_B, false);
  }

  @Test
  void onEntitlementsChanged_positive_failedBuildClearsBuildingFlag_nextEventRetries() {
    mockEntitlements(TENANT_NAME, APPLICATION_ID);
    when(appManagerService.getEgressBootstrap(List.of(APPLICATION_ID)))
      .thenReturn(failedFuture(new RuntimeException("am down")));

    service.onEntitlementsChanged(event(TENANT_NAME));
    service.onEntitlementsChanged(event(TENANT_NAME));

    verify(tenantEntitlementService, times(2)).getTenantEntitlements(TENANT_NAME, false);
  }

  @Test
  void onEntitlementsChanged_negative_bootstrapFails_tenantNotMarkedBuilt() {
    mockEntitlements(TENANT_NAME, APPLICATION_ID);
    when(appManagerService.getEgressBootstrap(List.of(APPLICATION_ID)))
      .thenReturn(failedFuture(new RuntimeException("am down")));

    service.onEntitlementsChanged(event(TENANT_NAME));
    service.onEntitlementsChanged(EntitlementsEvent.of(Set.of()));

    verify(egressRoutingLookup, never()).bindTenant(anyString(), anyString(), any(EgressRouteTable.class));
    verify(egressRoutingLookup, never()).removeTenant(anyString());
  }

  @Test
  void onEntitlementsChanged_positive_concurrencyLimited_queuedBuildStartsWhenSlotFrees() {
    initGuard(1);
    var firstEntitlements = Promise.<ResultList<Entitlement>>promise();
    when(tenantEntitlementService.getTenantEntitlements(TENANT_NAME, false)).thenReturn(firstEntitlements.future());

    service.onEntitlementsChanged(event(TENANT_NAME, TENANT_B));

    verify(tenantEntitlementService, never()).getTenantEntitlements(TENANT_B, false);

    when(tenantEntitlementService.getTenantEntitlements(TENANT_B, false))
      .thenReturn(succeededFuture(ResultList.empty()));
    firstEntitlements.complete(ResultList.empty());

    verify(tenantEntitlementService).getTenantEntitlements(TENANT_B, false);
  }

  @Test
  void onEntitlementsChanged_negative_buildThrows_slotReleased() {
    initGuard(1);
    when(tenantEntitlementService.getTenantEntitlements(TENANT_NAME, false))
      .thenThrow(new IllegalStateException("boom"));
    when(tenantEntitlementService.getTenantEntitlements(TENANT_B, false))
      .thenReturn(succeededFuture(ResultList.empty()));

    service.onEntitlementsChanged(event(TENANT_NAME, TENANT_B));

    verify(egressRoutingLookup).bindTenant(eq(TENANT_B), eq(""), any(EgressRouteTable.class));
  }

  @Test
  void onEntitlementsChanged_negative_disabledIsNoOp() {
    service.tenantScoped = false;

    service.onEntitlementsChanged(event(TENANT_NAME));

    verifyNoInteractions(appManagerService, tenantEntitlementService, egressRoutingLookup);
  }

  @Test
  void refreshTenant_positive_upgradeBindsNewApplicationSet() {
    mockEntitlements(TENANT_NAME, APP_A, APP_B);
    mockBootstrap(APP_A, APP_B);

    service.refreshTenant(TENANT_NAME);

    verify(egressRoutingLookup).bindTenant(eq(TENANT_NAME), eq(APP_A + "," + APP_B), any(EgressRouteTable.class));
  }

  @Test
  void refreshTenant_positive_staggerDelaysBuild() {
    service.refreshMaxStagger = Duration.ofSeconds(5);

    service.refreshTenant(TENANT_NAME);

    verify(vertx).setTimer(longThat(delay -> delay >= 1 && delay <= 5000), any());
    verifyNoInteractions(tenantEntitlementService);
  }

  @Test
  void refreshTenant_negative_disabledIsNoOp() {
    service.tenantScoped = false;

    service.refreshTenant(TENANT_NAME);

    verifyNoInteractions(tenantEntitlementService, appManagerService, egressRoutingLookup);
  }

  @Test
  void onDiscovery_positive_moduleInTable_updatesLocationWithoutBootstrap() {
    when(egressRoutingLookup.findTableKeys(any())).thenReturn(emptyList());
    when(egressRoutingLookup.routesToModule(MODULE_ID)).thenReturn(true);
    when(appManagerService.lookupModuleDiscovery(MODULE_ID))
      .thenReturn(succeededFuture(TestValues.moduleDiscovery(MODULE_ID, "mod-foo", "1.0.0", "http://new:8081")));

    service.onDiscovery(MODULE_ID);

    verify(egressRoutingLookup).updateModuleLocation(MODULE_ID, "http://new:8081");
    verify(appManagerService, never()).getEgressBootstrap(any());
  }

  @Test
  void onDiscovery_positive_discoveryDeleted_removesModule() {
    when(egressRoutingLookup.findTableKeys(any())).thenReturn(emptyList());
    when(egressRoutingLookup.routesToModule(MODULE_ID)).thenReturn(true);
    when(appManagerService.lookupModuleDiscovery(MODULE_ID))
      .thenReturn(failedFuture(new WebApplicationException("not found", 404)));

    service.onDiscovery(MODULE_ID);

    verify(egressRoutingLookup).removeModule(MODULE_ID);
    verify(appManagerService, never()).getEgressBootstrap(any());
  }

  @Test
  void onDiscovery_positive_recreatedAfterDelete_rebuildsOnlyAffectedTable() {
    when(egressRoutingLookup.findTableKeys(any())).thenReturn(List.of(APP_A));
    when(egressRoutingLookup.routesToModule(MODULE_ID)).thenReturn(false);
    mockBootstrap(APP_A);

    service.onDiscovery(MODULE_ID);

    verify(appManagerService).getEgressBootstrap(List.of(APP_A));
    verify(egressRoutingLookup).replaceTable(eq(APP_A), any(EgressRouteTable.class));
    verify(appManagerService, never()).lookupModuleDiscovery(anyString());
  }

  @Test
  void onDiscovery_positive_unknownModule_noCalls() {
    when(egressRoutingLookup.findTableKeys(any())).thenReturn(emptyList());
    when(egressRoutingLookup.routesToModule(MODULE_ID)).thenReturn(false);

    service.onDiscovery(MODULE_ID);

    verifyNoInteractions(appManagerService, tenantEntitlementService);
  }

  @Test
  void onDiscovery_negative_discoveryLookupFails_rebuildsTablesWithModule() {
    when(egressRoutingLookup.findTableKeys(any())).thenReturn(emptyList(), List.of(APP_A));
    when(egressRoutingLookup.routesToModule(MODULE_ID)).thenReturn(true);
    when(appManagerService.lookupModuleDiscovery(MODULE_ID)).thenReturn(failedFuture(new RuntimeException("down")));
    mockBootstrap(APP_A);

    service.onDiscovery(MODULE_ID);

    verify(appManagerService).getEgressBootstrap(List.of(APP_A));
    verify(egressRoutingLookup).replaceTable(eq(APP_A), any(EgressRouteTable.class));
    verify(egressRoutingLookup, never()).removeModule(anyString());
  }

  @Test
  void onDiscovery_negative_rebuildFails_tableKept() {
    when(egressRoutingLookup.findTableKeys(any())).thenReturn(List.of(APP_A));
    when(egressRoutingLookup.routesToModule(MODULE_ID)).thenReturn(false);
    when(appManagerService.getEgressBootstrap(List.of(APP_A))).thenReturn(failedFuture(new RuntimeException("down")));

    service.onDiscovery(MODULE_ID);

    verify(egressRoutingLookup, never()).replaceTable(anyString(), any(EgressRouteTable.class));
  }

  @Test
  void onDiscovery_negative_disabledIsNoOp() {
    service.tenantScoped = false;

    service.onDiscovery(MODULE_ID);

    verifyNoInteractions(appManagerService, tenantEntitlementService, egressRoutingLookup);
  }

  @Test
  void onDiscovery_positive_staggerDelaysRebuild() {
    service.refreshMaxStagger = Duration.ofSeconds(5);
    when(egressRoutingLookup.findTableKeys(any())).thenReturn(List.of(APP_A));
    when(egressRoutingLookup.routesToModule(MODULE_ID)).thenReturn(false);

    service.onDiscovery(MODULE_ID);

    verify(vertx).setTimer(longThat(delay -> delay >= 1 && delay <= 5000), any());
    verify(appManagerService, never()).getEgressBootstrap(any());
  }

  private void initGuard(int concurrency) {
    service.bootstrapConcurrency = concurrency;
    service.init();
  }

  private void mockEntitlements(String tenant, String... applicationIds) {
    var entitlements = Arrays.stream(applicationIds)
      .map(applicationId -> Entitlement.of(applicationId, TENANT_ID, emptyList()))
      .toArray(Entitlement[]::new);
    when(tenantEntitlementService.getTenantEntitlements(tenant, false))
      .thenReturn(succeededFuture(ResultList.asSinglePage(entitlements)));
  }

  private void mockBootstrap(String... applicationIds) {
    when(appManagerService.getEgressBootstrap(List.of(applicationIds)))
      .thenReturn(succeededFuture(MODULE_BOOTSTRAP_EGRESS));
  }

  private static EntitlementsEvent event(String... tenants) {
    return EntitlementsEvent.of(new LinkedHashSet<>(List.of(tenants)));
  }
}
