package org.folio.sidecar.configuration;

import io.quarkus.runtime.Startup;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.routing.RoutingService;

@Log4j2
@Startup
@ApplicationScoped
@RequiredArgsConstructor
public class RouterConfiguration {

  private final RoutingService routingService;
  private final SidecarProperties sidecarProperties;

  /**
   * Configures vertx {@link Router} on sidecar startup.
   *
   * <p>If routes were not populated</p>
   *
   * @param router - vertx {@link Router} object to configure
   */
  public void onStart(@Observes Router router) {
    log.info("Initializing sidecar: {}", sidecarProperties::getName);
    routingService.initRoutes(router);
  }
}
