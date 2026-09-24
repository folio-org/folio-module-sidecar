package org.folio.sidecar.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.folio.sidecar.service.routing.RoutingService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RoutingHealthCheckTest {

  @InjectMocks private RoutingHealthCheck routingHealthCheck;
  @Mock private RoutingService routingService;

  @Test
  void call_positive_routesInitialized() {
    when(routingService.isInitialized()).thenReturn(true);

    var response = routingHealthCheck.call();

    assertThat(response.getName()).isEqualTo(RoutingHealthCheck.CHECK_NAME);
    assertThat(response.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void call_negative_routesNotInitialized() {
    when(routingService.isInitialized()).thenReturn(false);

    var response = routingHealthCheck.call();

    assertThat(response.getName()).isEqualTo(RoutingHealthCheck.CHECK_NAME);
    assertThat(response.getStatus()).isEqualTo(Status.DOWN);
  }
}
