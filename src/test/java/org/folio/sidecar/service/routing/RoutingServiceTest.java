package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import jakarta.ws.rs.NotFoundException;
import org.folio.sidecar.service.routing.configuration.properties.TraceRoutingProperties;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.routing.handler.EgressRequestHandler;
import org.folio.sidecar.service.routing.handler.IngressRequestHandler;
import org.folio.sidecar.service.routing.lookup.RoutingLookupUtils;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

  @InjectMocks private RoutingService routingService;
  @Mock private Route route;
  @Mock private Router router;
  @Mock private ErrorHandler errorHandler;
  @Mock private EgressRequestHandler egressRequestHandler;
  @Mock private IngressRequestHandler ingressRequestHandler;
  @Mock private RoutingLookupUtils requestMatchingService;
  @Mock private ApplicationManagerService appManagerService;
  @Mock private TraceRoutingProperties traceRoutingProperties;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(errorHandler, egressRequestHandler,
      ingressRequestHandler, requestMatchingService, appManagerService);
  }

  @Test
  void initRoutes_positive() {
    var tracing = mock(TraceRoutingProperties.Tracing.class);
    when(traceRoutingProperties.tracing()).thenReturn(tracing);
    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(TestConstants.MODULE_BOOTSTRAP));
    doNothing().when(requestMatchingService).bootstrapModule(TestConstants.MODULE_BOOTSTRAP);

    when(router.route("/*")).thenReturn(route);
    when(route.handler(any())).thenReturn(route);

    routingService.initRoutes(router);

    verify(router).route("/*");
  }

  @Test
  void initRoutes_negative() {
    when(appManagerService.getModuleBootstrap()).thenReturn(failedFuture(new NotFoundException("not found")));

    routingService.initRoutes(router);

    verifyNoInteractions(router);
  }

  @Test
  void updateModuleRoutes_positive_updateIngressRoutes() {
    var tracing = mock(TraceRoutingProperties.Tracing.class);
    when(traceRoutingProperties.tracing()).thenReturn(tracing);
    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(TestConstants.MODULE_BOOTSTRAP));
    when(router.route("/*")).thenReturn(route);
    when(route.handler(any())).thenReturn(route);

    routingService.initRoutes(router);
    verify(router).route("/*");
    verify(requestMatchingService).bootstrapModule(TestConstants.MODULE_BOOTSTRAP);

    routingService.updateModuleRoutes(TestConstants.MODULE_ID);
    verify(requestMatchingService).updateIngressRoutes(TestConstants.MODULE_BOOTSTRAP.getModule());
  }

  @Test
  void updateModuleRoutes_positive_updateEgressRoutes() {
    var tracing = mock(TraceRoutingProperties.Tracing.class);
    when(traceRoutingProperties.tracing()).thenReturn(tracing);
    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(TestConstants.MODULE_BOOTSTRAP));
    when(router.route("/*")).thenReturn(route);
    when(route.handler(any())).thenReturn(route);

    routingService.initRoutes(router);
    verify(router).route("/*");
    verify(requestMatchingService).bootstrapModule(TestConstants.MODULE_BOOTSTRAP);

    routingService.updateModuleRoutes("mod-bar-0.5.1");
    verify(requestMatchingService).updateEgressRoutes(TestConstants.MODULE_BOOTSTRAP.getRequiredModules());
  }

  @Test
  void updateModuleRoutes_negative_moduleNotFound() {
    routingService.updateModuleRoutes("unknown_module");
    verifyNoInteractions(requestMatchingService);
  }
}
