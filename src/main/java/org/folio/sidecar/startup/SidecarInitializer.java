package org.folio.sidecar.startup;

import io.quarkus.runtime.Quarkus;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.RoutingService;
import org.folio.sidecar.service.routing.lookup.TenantEgressRoutingService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class SidecarInitializer {

  private final RoutingService routingService;
  private final SidecarProperties sidecarProperties;
  private final TenantService tenantService;
  private final TenantEgressRoutingService tenantEgressRoutingService;

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
    // 3. tenant-scoped egress routing (depends on tenants being loaded)
    routingService.init(router)
      .compose(unused -> tenantService.init())
      .compose(unused -> tenantEgressRoutingService.init())
      .onFailure(error -> {
        log.error("Failed to initialize tenant-scoped egress routing; failing startup", error);
        Quarkus.asyncExit(1);
      });
  }
}
