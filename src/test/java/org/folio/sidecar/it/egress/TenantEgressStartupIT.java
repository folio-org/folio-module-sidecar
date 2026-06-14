package org.folio.sidecar.it.egress;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.extensions.egress.ActiveTenantWireMock;
import org.folio.sidecar.support.profile.EgressStartupTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that on startup, when there is one active tenant whose entitlements include
 * this module and the AM egress bootstrap endpoint returns found=true,
 * EgressRoutingLookup has a route table for that tenant.
 */
@IntegrationTest
@TestProfile(EgressStartupTestProfile.class)
@QuarkusTestResource(value = ActiveTenantWireMock.class, restrictToAnnotatedClass = true)
class TenantEgressStartupIT {

  @Inject
  EgressRoutingLookup egressRoutingLookup;

  @BeforeAll
  @SneakyThrows
  static void waitForStartup() {
    // Allow async initialization (SidecarInitializer.onStart is async) to complete.
    Thread.sleep(3000);
  }

  @Test
  void startup_activeTenant_buildsEgressTableForTenant() {
    assertThat(egressRoutingLookup.hasTenant("testtenant"))
      .as("Expected egress route table to exist for active tenant 'testtenant'")
      .isTrue();
  }
}
