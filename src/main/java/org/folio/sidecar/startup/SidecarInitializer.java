package org.folio.sidecar.startup;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.RoutingService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class SidecarInitializer {

  private final RoutingService routingService;
  private final SidecarProperties sidecarProperties;
  private final TenantService tenantService;

  /**
   * Configures vertx {@link Router} on sidecar startup.
   *
   * <p>If routes were not populated</p>
   *
   * @param router - vertx {@link Router} object to configure
   */
  public void onStart(@Observes Router router) {
    log.info("Initializing sidecar: {}", sidecarProperties.getName());

    // chain of initialization:
    // 1. routing service and everything that depends on it
    // 2. tenant service
    routingService.init(router)
      .compose(unused -> tenantService.init());
  }
}
