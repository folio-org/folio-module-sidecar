package org.folio.sidecar.it.kafka;

import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.folio.sidecar.integration.kafka.TenantEntitlementConsumer;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(InMemoryMessagingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "entitlement")
})
@EnableWireMock
class TenantEntitlementConsumerIT {

  @InjectSpy TenantEntitlementConsumer consumer;
  @InjectMock TenantService tenantService;

  @Inject
  @Any
  InMemoryConnector connector;

  @Test
  void consume_positive_entitleEvent() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.ENTITLE);
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
