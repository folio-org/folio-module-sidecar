package org.folio.sidecar.it.egress;

import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.extensions.InjectWireMock;
import org.folio.sidecar.support.extensions.egress.Bootstrap404WireMock;
import org.folio.sidecar.support.profile.EgressBootstrap404TestProfile;
import org.folio.support.types.IntegrationTest;
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

  @InjectWireMock
  WireMockServer wireMockServer;

  @Test
  void startup_bootstrapEndpoint404_startsWithoutEgressTable() {
    // Wait until the AM POST /bootstrap call was attempted, proving egress init ran and handled 404.
    await().atMost(10, SECONDS)
      .untilAsserted(() -> wireMockServer.verify(moreThanOrExactly(1),
        postRequestedFor(urlPathMatching("/modules/.*/bootstrap"))));

    assertThat(egressRoutingLookup.hasTenant("testtenant"))
      .as("Expected NO egress table when bootstrap endpoint returns 404 (rollout fallback)")
      .isFalse();
  }
}
