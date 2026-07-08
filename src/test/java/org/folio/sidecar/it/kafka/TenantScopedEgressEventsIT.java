package org.folio.sidecar.it.kafka;

import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.profile.TenantScopedRoutingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(TenantScopedRoutingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "entitlement")
})
@EnableWireMock
class TenantScopedEgressEventsIT {

  @InjectSpy
  @Named("egressLookup")
  EgressRoutingLookup egressRoutingLookup;

  @Inject
  @Any
  InMemoryConnector connector;

  @Test
  void upgradeEvent_positive_rebuildsTenantEgress() {
    clearInvocations(egressRoutingLookup);

    sendEvent(TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.UPGRADE));

    awaitUntilAsserted(() ->
      verify(egressRoutingLookup, atLeastOnce()).updateTenantEgressRoutes(eq(TENANT_NAME), anyList()));
  }

  @Test
  void revokeEvent_positive_dropsTenantEgress() {
    // The EgressBootstrapService.tenants set is populated at startup (singleton, survives between tests).
    // Sending REVOKE triggers disableTenant -> EntitlementsEvent({}) -> dropTenant -> removeTenantEgressRoutes.
    // If revokeEvent runs first (before startup builds the set), the tenant won't be in the set;
    // so we send an UPGRADE first to ensure it's there, then REVOKE.
    sendEvent(TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.UPGRADE));
    awaitUntilAsserted(() ->
      verify(egressRoutingLookup, atLeastOnce()).updateTenantEgressRoutes(eq(TENANT_NAME), anyList()));

    clearInvocations(egressRoutingLookup);
    sendEvent(TenantEntitlementEvent.of(MODULE_ID, TENANT_NAME, TENANT_UUID, Type.REVOKE));

    awaitUntilAsserted(() ->
      verify(egressRoutingLookup, atLeastOnce()).removeTenantEgressRoutes(TENANT_NAME));
  }

  private void sendEvent(TenantEntitlementEvent event) {
    InMemorySource<TenantEntitlementEvent> source = connector.source("entitlement");
    source.send(event);
  }

  private static void awaitUntilAsserted(ThrowingRunnable runnable) {
    Awaitility.await()
      .atMost(Duration.ofSeconds(15))
      .pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(runnable);
  }
}
