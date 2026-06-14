package org.folio.sidecar.configuration;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.runtime.Quarkus;
import io.vertx.ext.web.Router;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.RoutingService;
import org.folio.sidecar.service.routing.lookup.TenantEgressRoutingService;
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
  @Mock private TenantEgressRoutingService tenantEgressRoutingService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(routingService, tenantService, tenantEgressRoutingService, sidecarProperties);
  }

  @Test
  void onStart_positive() {
    when(sidecarProperties.getName()).thenReturn("sc-mod-foo");
    when(routingService.init(router)).thenReturn(succeededFuture());
    when(tenantService.init()).thenReturn(succeededFuture());
    when(tenantEgressRoutingService.init()).thenReturn(succeededFuture());

    var initOrder = inOrder(routingService, tenantService, tenantEgressRoutingService);

    try (var quarkus = mockStatic(Quarkus.class)) {
      routerConfiguration.onStart(router);

      quarkus.verify(() -> Quarkus.asyncExit(org.mockito.ArgumentMatchers.anyInt()), never());
    }

    initOrder.verify(routingService).init(router);
    initOrder.verify(tenantService).init();
    initOrder.verify(tenantEgressRoutingService).init();
  }

  @Test
  void onStart_ingressFailure_failsStartup() {
    when(sidecarProperties.getName()).thenReturn("sc-mod-foo");
    when(routingService.init(router)).thenReturn(failedFuture(new RuntimeException("ingress down")));

    try (var quarkus = mockStatic(Quarkus.class)) {
      routerConfiguration.onStart(router);

      quarkus.verify(() -> Quarkus.asyncExit(1), times(1));
    }
  }

  @Test
  void onStart_egressFailure_recovered_doesNotFailStartup() {
    when(sidecarProperties.getName()).thenReturn("sc-mod-foo");
    when(routingService.init(router)).thenReturn(succeededFuture());
    when(tenantService.init()).thenReturn(succeededFuture());
    when(tenantEgressRoutingService.init()).thenReturn(failedFuture(new RuntimeException("egress down")));

    try (var quarkus = mockStatic(Quarkus.class)) {
      routerConfiguration.onStart(router);

      quarkus.verify(() -> Quarkus.asyncExit(org.mockito.ArgumentMatchers.anyInt()), never());
    }
  }
}
