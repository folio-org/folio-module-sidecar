package org.folio.sidecar.it.egress;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
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
import org.folio.sidecar.support.extensions.egress.NoActiveTenantWireMock;
import org.folio.sidecar.support.profile.EgressNoTenantTestProfile;
import org.folio.support.types.IntegrationTest;
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

  @InjectWireMock
  WireMockServer wireMockServer;

  @Test
  void startup_noActiveTenants_noEgressTables() {
    // Wait until the MTE module-entitlements call has been made, proving egress init has run.
    await().atMost(10, SECONDS)
      .untilAsserted(() -> wireMockServer.verify(moreThanOrExactly(1),
        getRequestedFor(urlPathMatching("/entitlements/modules/.*"))));

    assertThat(egressRoutingLookup.hasTenant("testtenant"))
      .as("Expected NO egress route table when module has no active tenants")
      .isFalse();
  }
}
