package org.folio.sidecar.configuration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.vertx.ext.web.Router;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.routing.RoutingService;
import org.folio.sidecar.startup.SidecarInitializer;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SidecarInitializerTest {

  @InjectMocks private SidecarInitializer routerConfiguration;

  @Mock private Router router;
  @Mock private RoutingService routingService;
  @Mock private SidecarProperties sidecarProperties;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(routingService);
  }

  @Test
  void onStart_positive() {
    routerConfiguration.onStart(router);
    verify(routingService).init(router);
  }
}
