package org.folio.sidecar.integration.kafka;

import static org.mockito.Mockito.verify;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.folio.sidecar.service.routing.RoutingService;
import org.folio.sidecar.support.profile.InMemoryMessagingResourceLifecycleManager;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
@QuarkusTest
@TestProfile(InMemoryMessagingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingResourceLifecycleManager.class, initArgs = {
  @ResourceArg(value = "incoming", name = "discovery")
})
class DiscoveryConsumerTest {

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

    verify(discoveryConsumer).consume(event);
    verify(routingService).updateModuleRoutes(MODULE_ID);
  }

  private void sendEvent(DiscoveryEvent event) {
    InMemorySource<DiscoveryEvent> discoveryIn = connector.source("discovery");
    discoveryIn.send(event);
  }
}
