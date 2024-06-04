package org.folio.sidecar.integration.kafka;

import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.support.profile.InMemoryMessagingResourceLifecycleManager;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@UnitTest
@QuarkusTest
@TestProfile(InMemoryMessagingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingResourceLifecycleManager.class, initArgs = {
  @ResourceArg(value = "incoming", name = "entitlement")
})
class TenantEntitlementConsumerTest {

  @InjectSpy TenantEntitlementConsumer consumer;
  @InjectMock TenantService tenantService;

  @Inject
  @Any
  InMemoryConnector connector;

  @ParameterizedTest
  @EnumSource(value = Type.class, names = {"ENTITLE", "UPGRADE"})
  void consume_positive_entitleOrUpgradeEvent(Type type) {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, type);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    sendEvent(event);

    verify(consumer).consume(event);
    verify(tenantService).enableTenant(TENANT_NAME);
  }

  @Test
  void consume_positive_revokeEvent() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.REVOKE);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);

    sendEvent(event);

    verify(consumer).consume(event);
    verify(tenantService).disableTenant(TENANT_NAME);
  }

  private void sendEvent(TenantEntitlementEvent event) {
    InMemorySource<TenantEntitlementEvent> discoveryIn = connector.source("entitlement");
    discoveryIn.send(event);
  }
}
