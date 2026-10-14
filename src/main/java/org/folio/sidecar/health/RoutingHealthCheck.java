package org.folio.sidecar.health;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.folio.sidecar.service.routing.RoutingService;

@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class RoutingHealthCheck implements HealthCheck {

  public static final String CHECK_NAME = "Routing health check";

  private final RoutingService routingService;

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named(CHECK_NAME).status(routingService.isInitialized()).build();
  }
}
