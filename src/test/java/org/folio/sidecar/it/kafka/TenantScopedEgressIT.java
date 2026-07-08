package org.folio.sidecar.it.kafka;

import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Named;
import org.awaitility.Awaitility;
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
class TenantScopedEgressIT {

  @InjectSpy
  @Named("egressLookup")
  EgressRoutingLookup egressRoutingLookup;

  @Test
  void startup_positive_buildsPerTenantEgress() {
    Awaitility.await()
      .atMost(TEN_SECONDS)
      .pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() ->
        verify(egressRoutingLookup, atLeastOnce()).updateTenantEgressRoutes(eq(TENANT_NAME), anyList()));
  }
}
