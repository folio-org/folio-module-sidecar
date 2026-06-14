package org.folio.sidecar.it.kafka;

import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import io.vertx.core.Future;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.sidecar.integration.kafka.TenantEntitlementConsumer;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.lookup.TenantEgressRoutingService;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@IntegrationTest
@TestProfile(InMemoryMessagingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "entitlement")
})
@EnableWireMock
class TenantEntitlementConsumerIT {

  @InjectSpy TenantEntitlementConsumer consumer;
  @InjectMock TenantService tenantService;
  @InjectMock TenantEgressRoutingService tenantEgressRoutingService;

  @Inject
  @Any
  InMemoryConnector connector;

  @ParameterizedTest
  @EnumSource(value = Type.class, names = {"ENTITLE", "UPGRADE"})
  void consume_positive_entitleOrUpgradeEvent(Type type) {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, type);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    when(tenantEgressRoutingService.refreshTenant(TENANT_NAME)).thenReturn(Future.succeededFuture());

    sendEvent(event);

    awaitUntilAsserted(() -> verify(consumer).consume(event));
    verify(tenantService).enableTenant(TENANT_NAME);
    verify(tenantEgressRoutingService).refreshTenant(TENANT_NAME);
  }

  @Test
  void consume_positive_revokeEvent() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.REVOKE);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    when(tenantEgressRoutingService.refreshTenant(TENANT_NAME)).thenReturn(Future.succeededFuture());

    sendEvent(event);

    awaitUntilAsserted(() -> verify(consumer).consume(event));
    verify(tenantService).disableTenant(TENANT_NAME);
    verify(tenantEgressRoutingService).refreshTenant(TENANT_NAME);
  }

  @Test
  void consume_negative_notAssignedModule() {
    var event = TenantEntitlementEvent.of("mod-other-1.0.0", TENANT_NAME, TENANT_UUID, Type.ENTITLE);
    when(tenantService.isAssignedModule("mod-other-1.0.0")).thenReturn(false);

    sendEvent(event);

    awaitUntilAsserted(() -> verify(consumer).consume(event));
    verify(tenantService, never()).enableTenant(any());
    verify(tenantService, never()).disableTenant(any());
    verify(tenantEgressRoutingService, never()).refreshTenant(any());
  }

  private void sendEvent(TenantEntitlementEvent event) {
    InMemorySource<TenantEntitlementEvent> discoveryIn = connector.source("entitlement");
    discoveryIn.send(event);
  }

  private static void awaitUntilAsserted(ThrowingRunnable runnable) {
    Awaitility.await()
      .atMost(FIVE_SECONDS)
      .pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(runnable);
  }
}
