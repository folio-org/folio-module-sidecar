package org.folio.sidecar.it.kafka;

import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.mockito.Mockito.verify;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.sidecar.integration.kafka.DiscoveryConsumer;
import org.folio.sidecar.integration.kafka.DiscoveryEvent;
import org.folio.sidecar.service.routing.RoutingService;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(InMemoryMessagingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "discovery")
})
@EnableWireMock
class DiscoveryConsumerIT {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @InjectSpy DiscoveryConsumer discoveryConsumer;
  @InjectMock RoutingService routingService;

  @Inject
  @Any
  InMemoryConnector connector;

  @Test
  void consume_positive() {
    DiscoveryEvent event = DiscoveryEvent.of(MODULE_ID);
    sendEvent(event);

    awaitUntilAsserted(() -> verify(discoveryConsumer).consume(event));
    verify(routingService).onDiscovery(MODULE_ID);
  }

  private void sendEvent(DiscoveryEvent event) {
    InMemorySource<DiscoveryEvent> discoveryIn = connector.source("discovery");
    discoveryIn.send(event);
  }

  private static void awaitUntilAsserted(ThrowingRunnable runnable) {
    Awaitility.await()
      .atMost(FIVE_SECONDS)
      .pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(runnable);
  }
}
