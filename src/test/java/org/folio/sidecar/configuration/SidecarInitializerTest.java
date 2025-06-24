package org.folio.sidecar.configuration;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.Router;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.TenantService;
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
  @Mock private TenantService tenantService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(routingService, tenantService, sidecarProperties);
  }

  @Test
  void onStart_positive() {
    when(sidecarProperties.getName()).thenReturn("sc-mod-foo");
    when(routingService.init(router)).thenReturn(succeededFuture());
    when(tenantService.init()).thenReturn(succeededFuture());

    var initOrder = inOrder(routingService, tenantService);

    routerConfiguration.onStart(router);

    initOrder.verify(routingService).init(router);
    initOrder.verify(tenantService).init();
  }
}
