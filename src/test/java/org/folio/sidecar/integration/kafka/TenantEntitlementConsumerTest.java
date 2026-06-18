package org.folio.sidecar.integration.kafka;

import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.ENTITLE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.REVOKE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.UPGRADE;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.EgressBootstrapService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantEntitlementConsumerTest {

  @Mock private TenantService tenantService;
  @Mock private EgressBootstrapService egressBootstrapService;

  private TenantEntitlementConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new TenantEntitlementConsumer(tenantService, egressBootstrapService);
    consumer.tenantScoped = true;
  }

  @Test
  void consume_positive_upgradeRefreshesTenant() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, UPGRADE));

    verify(tenantService).enableTenant(TENANT_NAME);
    verify(egressBootstrapService).refreshTenant(TENANT_NAME);
  }

  @Test
  void consume_positive_entitleDoesNotRefreshTenant() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, ENTITLE));

    verify(tenantService).enableTenant(TENANT_NAME);
    verifyNoInteractions(egressBootstrapService);
  }

  @Test
  void consume_positive_revokeDisablesTenant() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, REVOKE));

    verify(tenantService).disableTenant(TENANT_NAME);
    verifyNoInteractions(egressBootstrapService);
  }

  @Test
  void consume_negative_notAssignedModuleIsIgnored() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(false);

    consumer.consume(TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, UPGRADE));

    verifyNoInteractions(egressBootstrapService);
  }
}
