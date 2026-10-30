package org.folio.sidecar.it.kafka;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.folio.sidecar.integration.kafka.DiscoveryEvent;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.extensions.InjectWireMock;
import org.folio.sidecar.support.profile.TenantScopedRoutingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves that, in tenant-scoped mode, a discovery event of a provider module updates the egress table in place, with
 * no new egress bootstrap, and that a deleted and re-created discovery restores the routes with one rebuild.
 *
 * <p>The startup build binds {@code testtenant} to the table of {@code application-0.0.1}, whose only provider is
 * {@code mod-bar-0.5.1}. The table is read directly from {@link EgressRoutingLookup}, so no egress request is needed.
 * Application-scoped state survives between tests, so every test moves the provider to a location of its own.</p>
 */
@IntegrationTest
@EnableWireMock
@TestProfile(TenantScopedRoutingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "entitlement"),
  @ResourceArg(value = "incoming", name = "discovery")
})
class TenantScopedEgressDiscoveryIT {

  private static final String TABLE_KEY = "application-0.0.1";
  private static final String PROVIDER_ID = "mod-bar-0.5.1";
  private static final String BOOTSTRAP_LOCATION = "http://sc-bar:8081";
  private static final String MOVED_LOCATION = "http://sc-bar-moved:8081";
  private static final String OTHER_LOCATION = "http://sc-bar-other:8081";
  private static final String BOOTSTRAP_PATH = "/modules/" + MODULE_ID + "/bootstrap";

  @InjectWireMock WireMockServer wireMockServer;
  @Inject @Named("egressLookup") EgressRoutingLookup egressRoutingLookup;
  @Inject @Any InMemoryConnector connector;

  private final List<StubMapping> stubs = new ArrayList<>();

  @BeforeEach
  void awaitStartupBuild() {
    await().atMost(Duration.ofSeconds(15)).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .until(() -> egressRoutingLookup.getTable(TABLE_KEY) != null);
  }

  @AfterEach
  void removeStubs() {
    stubs.forEach(wireMockServer::removeStub);
    stubs.clear();
  }

  @Test
  void discoveryEvent_positive_updatesLocationInPlaceWithoutBootstrap() {
    final int bootstrapCalls = bootstrapCalls();
    stubDiscovery(PROVIDER_ID, 200, MOVED_LOCATION);

    sendDiscoveryEvent(PROVIDER_ID);

    awaitUntil(() -> MOVED_LOCATION.equals(providerLocation()));
    assertThat(bootstrapCalls()).isEqualTo(bootstrapCalls);
  }

  @Test
  void discoveryEvent_positive_deletedThenRecreatedDiscoveryRestoresRoutes() {
    stubDiscovery(PROVIDER_ID, 404, null);
    sendDiscoveryEvent(PROVIDER_ID);
    awaitUntil(() -> !egressRoutingLookup.routesToModule(PROVIDER_ID));
    final int bootstrapCalls = bootstrapCalls();
    assertThat(egressRoutingLookup.findTableKeys(table -> table.isRemoved(PROVIDER_ID))).containsExactly(TABLE_KEY);

    sendDiscoveryEvent(PROVIDER_ID);

    awaitUntil(() -> BOOTSTRAP_LOCATION.equals(providerLocation()));
    assertThat(bootstrapCalls()).isEqualTo(bootstrapCalls + 1);
    assertThat(egressRoutingLookup.findTableKeys(table -> table.isRemoved(PROVIDER_ID))).isEmpty();
  }

  @Test
  void discoveryEvent_positive_unknownModuleCausesNoCalls() {
    final int bootstrapCalls = bootstrapCalls();
    stubDiscovery("mod-unknown-1.0.0", 200, OTHER_LOCATION);
    stubDiscovery(PROVIDER_ID, 200, OTHER_LOCATION);

    sendDiscoveryEvent("mod-unknown-1.0.0");
    sendDiscoveryEvent(PROVIDER_ID);

    // events are handled in order, so once the second one is applied the first one is done
    awaitUntil(() -> OTHER_LOCATION.equals(providerLocation()));
    assertThat(wireMockServer.countRequestsMatching(
      getRequestedFor(urlPathEqualTo("/modules/mod-unknown-1.0.0/discovery")).build()).getCount()).isZero();
    assertThat(bootstrapCalls()).isEqualTo(bootstrapCalls);
  }

  private void stubDiscovery(String moduleId, int status, String location) {
    var response = aResponse().withStatus(status).withHeader("Content-Type", "application/json");
    if (status == 200) {
      response.withBody("{\"id\": \"%s\", \"name\": \"mod-bar\", \"version\": \"0.5.1\", \"location\": \"%s\"}"
        .formatted(moduleId, location));
    }
    stubs.add(wireMockServer.stubFor(get(urlPathEqualTo("/modules/" + moduleId + "/discovery")).willReturn(response)));
  }

  private String providerLocation() {
    var table = egressRoutingLookup.getTable(TABLE_KEY);
    return table.routes().values().stream()
      .flatMap(List::stream)
      .filter(entry -> PROVIDER_ID.equals(entry.getModuleId()))
      .map(ScRoutingEntry::getLocation)
      .findFirst()
      .orElse(null);
  }

  private int bootstrapCalls() {
    return wireMockServer.countRequestsMatching(postRequestedFor(urlPathEqualTo(BOOTSTRAP_PATH)).build()).getCount();
  }

  private void sendDiscoveryEvent(String moduleId) {
    InMemorySource<DiscoveryEvent> source = connector.source("discovery");
    source.send(DiscoveryEvent.of(moduleId));
  }

  private static void awaitUntil(Supplier<Boolean> condition) {
    await().atMost(Duration.ofSeconds(15)).pollDelay(ONE_HUNDRED_MILLISECONDS).until(condition::get);
  }
}
