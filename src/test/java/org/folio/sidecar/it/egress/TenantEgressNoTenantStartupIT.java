package org.folio.sidecar.it.egress;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.extensions.egress.NoActiveTenantWireMock;
import org.folio.sidecar.support.profile.EgressNoTenantTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that when MTE reports no active tenant entitlements for this module,
 * startup succeeds and EgressRoutingLookup has no route tables.
 *
 * <p>The test running at all proves startup did not fail.
 */
@IntegrationTest
@TestProfile(EgressNoTenantTestProfile.class)
@QuarkusTestResource(value = NoActiveTenantWireMock.class, restrictToAnnotatedClass = true)
class TenantEgressNoTenantStartupIT {

  @Inject
  EgressRoutingLookup egressRoutingLookup;

  @BeforeAll
  @SneakyThrows
  static void waitForStartup() {
    Thread.sleep(3000);
  }

  @Test
  void startup_noActiveTenants_noEgressTables() {
    assertThat(egressRoutingLookup.hasTenant("testtenant"))
      .as("Expected NO egress route table when module has no active tenants")
      .isFalse();
  }
}
