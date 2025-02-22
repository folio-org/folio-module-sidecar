package org.folio.sidecar.integration.kafka;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Log4j2
@ApplicationScoped
public class DiscoveryConsumer {

  private final List<DiscoveryListener> discoveryListeners;

  public DiscoveryConsumer(@All List<DiscoveryListener> listeners) {
    this.discoveryListeners = listeners;
  }

  @Incoming("discovery")
  public void consume(DiscoveryEvent discovery) {
    log.debug("Consuming discovery event: {}", discovery);
    discoveryListeners.forEach(listener -> listener.onDiscovery(discovery.getModuleId()));
  }
}
