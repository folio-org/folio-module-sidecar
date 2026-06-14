package org.folio.sidecar.startup;

import io.quarkus.runtime.Quarkus;
import io.vertx.core.Future;
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

    // Startup chain:
    // 1. ingress routing (own module) — failure FAILS startup
    // 2. tenant service — failure FAILS startup
    // 3. tenant-scoped egress — best-effort; failure is recovered (tenants forward egress to the
    //    gateway until a later refresh succeeds), so it never fails startup
    routingService.init(router)
      .compose(unused -> tenantService.init())
      .compose(unused -> tenantEgressRoutingService.init()
        .recover(error -> {
          log.warn("Scoped egress initialization failed; tenants without a scoped table will forward egress "
            + "to the gateway until refreshed", error);
          return Future.succeededFuture();
        }))
      .onFailure(error -> {
        log.error("Failed to initialize sidecar startup; shutting down", error);
        Quarkus.asyncExit(1);
      });
  }
}
