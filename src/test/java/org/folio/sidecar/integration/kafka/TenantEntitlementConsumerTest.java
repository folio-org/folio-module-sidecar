package org.folio.sidecar.integration.kafka;

import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.ENTITLE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.REVOKE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.UPGRADE;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.EgressBootstrapService;
import org.folio.sidecar.service.routing.lookup.TenantModuleResolver;
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
  @Mock private TenantModuleResolver tenantModuleResolver;

  private TenantEntitlementConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new TenantEntitlementConsumer(tenantService, egressBootstrapService, tenantModuleResolver);
    consumer.tenantScoped = true;
  }

  @Test
  void consume_positive_upgradeRefreshesTenant() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, UPGRADE);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(event);

    verify(tenantService).enableTenant(TENANT_NAME);
    verify(egressBootstrapService).refreshTenant(TENANT_NAME);
    verify(tenantModuleResolver).onEntitlementEvent(event);
  }

  @Test
  void consume_positive_entitleDoesNotRefreshTenant() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, ENTITLE);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(event);

    verify(tenantService).enableTenant(TENANT_NAME);
    verify(tenantModuleResolver).onEntitlementEvent(event);
    verifyNoInteractions(egressBootstrapService);
  }

  @Test
  void consume_positive_revokeDisablesTenant() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, REVOKE);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(event);

    verify(tenantService).disableTenant(TENANT_NAME);
    verify(tenantModuleResolver).onEntitlementEvent(event);
    verifyNoInteractions(egressBootstrapService);
  }

  @Test
  void consume_positive_upgradeDoesNotRefreshWhenDisabled() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, UPGRADE);
    consumer.tenantScoped = false;
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(event);

    verify(tenantService).enableTenant(TENANT_NAME);
    verify(tenantModuleResolver).onEntitlementEvent(event);
    verifyNoInteractions(egressBootstrapService);
  }

  @Test
  void consume_negative_notAssignedModuleIsIgnored() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, UPGRADE);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(false);

    consumer.consume(event);

    verify(tenantService).isAssignedModule(MODULE_ID);
    verify(tenantModuleResolver).onEntitlementEvent(event);
    verifyNoMoreInteractions(tenantService);
    verifyNoInteractions(egressBootstrapService);
  }

  /** The events this feature needs are about another module, so the notification runs before that guard. */
  @Test
  void consume_positive_notifiesModuleResolverForOtherModule() {
    var event = TenantEntitlementEvent.of("mod-users-keycloak-4.0.2", TENANT_NAME, TENANT_UUID, UPGRADE);
    when(tenantService.isAssignedModule("mod-users-keycloak-4.0.2")).thenReturn(false);

    consumer.consume(event);

    verify(tenantModuleResolver).onEntitlementEvent(event);
    verifyNoInteractions(egressBootstrapService);
  }

  /** A defect in the target service must not nack the message and stop the entitlement channel. */
  @Test
  void consume_positive_survivesModuleResolverFailure() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, ENTITLE);
    doThrow(new IllegalStateException("boom")).when(tenantModuleResolver).onEntitlementEvent(event);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    consumer.consume(event);

    verify(tenantService).enableTenant(TENANT_NAME);
  }
}
