package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.NotFoundException;
import java.util.stream.Stream;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

  private RoutingService routingService;
  @Mock private Route route;
  @Mock private Router router;
  @Mock private ApplicationManagerService appManagerService;
  @Mock private Handler<RoutingContext> requestHandler;
  @Mock private Instance<ModuleBootstrapListener> moduleBootstrapListeners;
  @Mock private ModuleBootstrapListener listener1;
  @Mock private ModuleBootstrapListener listener2;

  @BeforeEach
  void setUp() {
    when(moduleBootstrapListeners.stream()).thenReturn(Stream.of(listener1, listener2));
    routingService = new RoutingService(appManagerService, requestHandler, moduleBootstrapListeners);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(appManagerService, requestHandler, listener1, listener2);
  }

  @Test
  void initRoutes_positive() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));
    when(router.route("/*")).thenReturn(route);

    var listenersOrder = inOrder(listener1, listener2);

    routingService.initRoutes(router);

    listenersOrder.verify(listener1).onModuleBootstrap(bootstrap.getModule(), INIT);
    listenersOrder.verify(listener1).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), INIT);
    listenersOrder.verify(listener2).onModuleBootstrap(bootstrap.getModule(), INIT);
    listenersOrder.verify(listener2).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), INIT);
    verify(router).route("/*");
    verify(route).handler(requestHandler);
  }

  @Test
  void initRoutes_negative() {
    when(appManagerService.getModuleBootstrap()).thenReturn(failedFuture(new NotFoundException("not found")));

    routingService.initRoutes(router);

    verifyNoInteractions(router);
  }

  @Test
  void updateModuleRoutes_positive_updateIngressRoutes() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));
    when(router.route("/*")).thenReturn(route);

    routingService.initRoutes(router);
    reset(listener1, listener2, router, route);

    var listenersOrder = inOrder(listener1, listener2);

    routingService.updateModuleRoutes(TestConstants.MODULE_ID);

    listenersOrder.verify(listener1).onModuleBootstrap(bootstrap.getModule(), UPDATE);
    listenersOrder.verify(listener2).onModuleBootstrap(bootstrap.getModule(), UPDATE);
  }

  @Test
  void updateModuleRoutes_positive_updateEgressRoutes() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));
    when(router.route("/*")).thenReturn(route);

    routingService.initRoutes(router);
    reset(listener1, listener2, router, route);

    var listenersOrder = inOrder(listener1, listener2);

    routingService.updateModuleRoutes("mod-bar-0.5.1");

    listenersOrder.verify(listener1).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), UPDATE);
    listenersOrder.verify(listener2).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), UPDATE);
  }

  @Test
  void updateModuleRoutes_negative_moduleNotFound() {
    routingService.updateModuleRoutes("unknown_module");
    verifyNoInteractions(appManagerService, listener1, listener2, router, route);
  }
}
