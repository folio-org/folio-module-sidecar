package org.folio.sidecar.it.kafka;

import static io.vertx.core.Future.succeededFuture;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.sidecar.support.TestConstants.APPLICATION_ID;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Set;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.kafka.TenantEntitlementConsumer;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
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
  @InjectMock ApplicationManagerService applicationManagerService;
  @InjectMock EgressRoutingLookup egressRoutingLookup;

  @Inject
  @Any
  InMemoryConnector connector;

  @ParameterizedTest
  @EnumSource(value = Type.class, names = {"ENTITLE", "UPGRADE"})
  void consume_positive_entitleOrUpgradeEvent(Type type) {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, type, APPLICATION_ID);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    when(applicationManagerService.getModuleBootstrap(APPLICATION_ID)).thenReturn(succeededFuture(MODULE_BOOTSTRAP));

    sendEvent(event);

    awaitUntilAsserted(() -> verify(consumer).consume(event));
    verify(tenantService).enableTenant(TENANT_NAME, APPLICATION_ID);
    verify(applicationManagerService).getModuleBootstrap(APPLICATION_ID);
    verify(egressRoutingLookup).onApplicationBootstrap(eq(APPLICATION_ID), anyList());
  }

  @Test
  void consume_positive_revokeEvent_lastTenant_revokesEgressCache() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.REVOKE, APPLICATION_ID);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    // After disableTenant, no other tenant uses this applicationId
    when(tenantService.getAllApplicationIds()).thenReturn(Collections.emptySet());

    sendEvent(event);

    awaitUntilAsserted(() -> verify(consumer).consume(event));
    verify(tenantService).disableTenant(TENANT_NAME, APPLICATION_ID);
    verify(egressRoutingLookup).onApplicationRevoked(APPLICATION_ID);
  }

  @Test
  void consume_positive_revokeEvent_otherTenantPresent_keepsEgressCache() {
    var event = TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.REVOKE, APPLICATION_ID);
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    // Another tenant still uses this applicationId
    when(tenantService.getAllApplicationIds()).thenReturn(Set.of(APPLICATION_ID));

    sendEvent(event);

    awaitUntilAsserted(() -> verify(consumer).consume(event));
    verify(tenantService).disableTenant(TENANT_NAME, APPLICATION_ID);
    verify(egressRoutingLookup, never()).onApplicationRevoked(APPLICATION_ID);
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
