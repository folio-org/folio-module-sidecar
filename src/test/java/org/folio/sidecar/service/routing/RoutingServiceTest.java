package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.service.ModulePermissionsService;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

  private RoutingService routingService;
  @Mock private Route route;
  @Mock private Router router;
  @Mock private ApplicationManagerService appManagerService;
  @Mock private Handler<RoutingContext> requestHandler1;
  @Mock private Handler<RoutingContext> requestHandler2;
  @Mock private ModuleBootstrapListener listener1;
  @Mock private ModuleBootstrapListener listener2;
  @Mock private ModulePermissionsService modulePermissionsService;

  @BeforeEach
  void setUp() {
    routingService = new RoutingService(appManagerService,
      List.of(requestHandler1, requestHandler2), List.of(listener1, listener2),
      modulePermissionsService);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(appManagerService, requestHandler1, requestHandler2, listener1, listener2,
      modulePermissionsService);
  }

  @Test
  void constructor_negative_noRequestHandlers() {
    var listeners = List.of(listener1, listener2);
    var handlers = List.<Handler<RoutingContext>>of();
    
    Assertions.assertThatThrownBy(() -> new RoutingService(appManagerService, handlers,
        listeners, modulePermissionsService))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Request handlers are not configured");
  }

  @Test
  void init_positive() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));
    when(router.route("/*")).thenReturn(route);

    var listenersOrder = inOrder(listener1, listener2);
    var routeOrder = inOrder(route);

    routingService.init(router);

    verifyListeners(listenersOrder, bootstrap);
    verifyHandlers(routeOrder);

    verify(modulePermissionsService).putPermissions(anySet());
  }

  @Test
  void init_negative() {
    when(appManagerService.getModuleBootstrap()).thenReturn(failedFuture(new NotFoundException("not found")));

    routingService.init(router);

    verifyNoInteractions(router);
  }

  @Test
  void updateModuleRoutes_positive_updateIngressRoutes() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));
    when(router.route("/*")).thenReturn(route);

    routingService.init(router);
    reset(listener1, listener2, router, route);

    var listenersOrder = inOrder(listener1, listener2);

    routingService.updateModuleRoutes(TestConstants.MODULE_ID);

    listenersOrder.verify(listener1).onModuleBootstrap(bootstrap.getModule(), UPDATE);
    listenersOrder.verify(listener2).onModuleBootstrap(bootstrap.getModule(), UPDATE);
    verify(modulePermissionsService, times(2)).putPermissions(anySet());
  }

  @Test
  void updateModuleRoutes_positive_updateEgressRoutes() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));
    when(router.route("/*")).thenReturn(route);

    routingService.init(router);
    reset(listener1, listener2, router, route);

    var listenersOrder = inOrder(listener1, listener2);

    routingService.updateModuleRoutes("mod-bar-0.5.1");

    listenersOrder.verify(listener1).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), UPDATE);
    listenersOrder.verify(listener2).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), UPDATE);
    verify(modulePermissionsService).putPermissions(anySet());
  }

  @Test
  void updateModuleRoutes_negative_moduleNotFound() {
    routingService.updateModuleRoutes("unknown_module");
    verifyNoInteractions(appManagerService, listener1, listener2, router, route);
  }

  private void verifyHandlers(InOrder routeOrder) {
    routeOrder.verify(route).handler(requestHandler1);
    routeOrder.verify(route).handler(requestHandler2);
  }

  private void verifyListeners(InOrder listenersOrder, ModuleBootstrap bootstrap) {
    listenersOrder.verify(listener1).onModuleBootstrap(bootstrap.getModule(), INIT);
    listenersOrder.verify(listener1).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), INIT);
    listenersOrder.verify(listener2).onModuleBootstrap(bootstrap.getModule(), INIT);
    listenersOrder.verify(listener2).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), INIT);
  }
}
