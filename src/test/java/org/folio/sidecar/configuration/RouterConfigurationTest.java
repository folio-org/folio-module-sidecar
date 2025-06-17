package org.folio.sidecar.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.routing.RoutingService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RouterConfigurationTest {

  @InjectMocks private RouterConfiguration routerConfiguration;

  @Mock private Route route;
  @Mock private Router router;
  @Mock private RoutingService routingService;
  @Mock private SidecarProperties sidecarProperties;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(routingService);
  }

  @Test
  void onStart_positive() {
    when(router.route()).thenReturn(route);
    when(route.method(HttpMethod.GET)).thenReturn(route);
    when(route.handler(any())).thenReturn(route);

    routerConfiguration.onStart(router);
    verify(routingService).initRoutes(router);
  }
}
