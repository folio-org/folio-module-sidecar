package org.folio.sidecar.it.egress;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.extensions.egress.Bootstrap404WireMock;
import org.folio.sidecar.support.profile.EgressBootstrap404TestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the rollout-fallback behavior: when there is an active tenant but the AM
 * POST /modules/{id}/bootstrap endpoint returns 404 (not yet deployed),
 * startup succeeds and EgressRoutingLookup has no table for that tenant.
 *
 * <p>The test running at all proves startup did not fail (no Quarkus.asyncExit(1)).
 */
@IntegrationTest
@TestProfile(EgressBootstrap404TestProfile.class)
@QuarkusTestResource(value = Bootstrap404WireMock.class, restrictToAnnotatedClass = true)
class TenantEgressBootstrap404StartupIT {

  @Inject
  EgressRoutingLookup egressRoutingLookup;

  @BeforeAll
  @SneakyThrows
  static void waitForStartup() {
    Thread.sleep(3000);
  }

  @Test
  void startup_bootstrapEndpoint404_startsWithoutEgressTable() {
    assertThat(egressRoutingLookup.hasTenant("testtenant"))
      .as("Expected NO egress table when bootstrap endpoint returns 404 (rollout fallback)")
      .isFalse();
  }
}
