package org.folio.sidecar.integration.kafka;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.folio.sidecar.service.routing.RoutingService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class DiscoveryConsumer {

  private final RoutingService routingService;

  @Incoming("discovery")
  public void consume(DiscoveryEvent discovery) {
    log.debug("Consuming discovery event: {}", discovery);
    routingService.updateModuleRoutes(discovery.getModuleId());
  }
}
