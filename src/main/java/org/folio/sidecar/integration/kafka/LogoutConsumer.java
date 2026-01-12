package org.folio.sidecar.integration.kafka;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.folio.sidecar.service.CacheInvalidatable;

@Log4j2
@ApplicationScoped
public class LogoutConsumer {

  private final List<CacheInvalidatable> caches;

  @Inject
  public LogoutConsumer(@All List<CacheInvalidatable> caches) {
    this.caches = caches;
  }

  @Incoming("logout")
  public void consume(LogoutEvent event) {
    log.info("LogoutConsumer: Received logout event [type={}, userId={}, sessionId={}]",
      event.getType(), event.getUserId(), event.getSessionId());
    log.debug("LogoutConsumer: Invalidating {} caches: {}",
      caches::size,
      () -> caches.stream().map(c -> c.getClass().getSimpleName()).toList());
    caches.forEach(cache -> cache.invalidate(event));
  }
}
