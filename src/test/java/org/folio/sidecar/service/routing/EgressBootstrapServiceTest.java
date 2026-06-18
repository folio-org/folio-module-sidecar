package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.folio.sidecar.support.TestConstants.APPLICATION_ID;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP_EGRESS;
import static org.folio.sidecar.support.TestConstants.TENANT_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EgressBootstrapServiceTest {

  @Mock private ApplicationManagerService appManagerService;
  @Mock private TenantEntitlementService tenantEntitlementService;
  @Mock private EgressRoutingLookup egressRoutingLookup;

  private EgressBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new EgressBootstrapService(appManagerService, tenantEntitlementService, egressRoutingLookup);
    service.tenantScoped = true;
  }

  @Test
  void onEntitlementsChanged_positive_buildsAddedTenant() {
    mockEntitlements(TENANT_NAME);
    when(appManagerService.getEgressBootstrap(List.of(APPLICATION_ID)))
      .thenReturn(succeededFuture(MODULE_BOOTSTRAP_EGRESS));

    service.onEntitlementsChanged(EntitlementsEvent.of(Set.of(TENANT_NAME)));

    verify(egressRoutingLookup).updateTenantEgressRoutes(TENANT_NAME, MODULE_BOOTSTRAP_EGRESS.getRequiredModules());
  }

  @Test
  void onEntitlementsChanged_positive_dropsRemovedTenant() {
    mockEntitlements(TENANT_NAME);
    when(appManagerService.getEgressBootstrap(List.of(APPLICATION_ID)))
      .thenReturn(succeededFuture(MODULE_BOOTSTRAP_EGRESS));
    service.onEntitlementsChanged(EntitlementsEvent.of(Set.of(TENANT_NAME)));

    service.onEntitlementsChanged(EntitlementsEvent.of(Set.of()));

    verify(egressRoutingLookup).removeTenantEgressRoutes(TENANT_NAME);
  }

  @Test
  void refreshTenant_positive_emptyApplicationsStoresEmptyTable() {
    when(tenantEntitlementService.getTenantEntitlements(TENANT_NAME, false))
      .thenReturn(succeededFuture(ResultList.empty()));

    service.refreshTenant(TENANT_NAME);

    verify(egressRoutingLookup).updateTenantEgressRoutes(TENANT_NAME, emptyList());
    verify(appManagerService, never()).getEgressBootstrap(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void refreshTenant_negative_disabledIsNoOp() {
    service.tenantScoped = false;

    service.refreshTenant(TENANT_NAME);

    verifyNoInteractions(tenantEntitlementService, appManagerService, egressRoutingLookup);
  }

  @Test
  void onEntitlementsChanged_negative_buildFailureKeepsPreviousTable() {
    mockEntitlements(TENANT_NAME);
    when(appManagerService.getEgressBootstrap(List.of(APPLICATION_ID)))
      .thenReturn(failedFuture(new RuntimeException("am down")));

    service.onEntitlementsChanged(EntitlementsEvent.of(Set.of(TENANT_NAME)));

    verify(egressRoutingLookup, never()).updateTenantEgressRoutes(anyString(), anyList());
  }

  private void mockEntitlements(String tenant) {
    when(tenantEntitlementService.getTenantEntitlements(tenant, false))
      .thenReturn(succeededFuture(ResultList.asSinglePage(Entitlement.of(APPLICATION_ID, TENANT_ID, emptyList()))));
  }
}
