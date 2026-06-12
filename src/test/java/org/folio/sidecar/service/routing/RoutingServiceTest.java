package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.service.ModulePermissionsService;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
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
  @Mock private TenantService tenantService;
  @Mock private EgressRoutingLookup egressLookup;

  @BeforeEach
  void setUp() {
    routingService = new RoutingService(appManagerService,
      List.of(requestHandler1, requestHandler2), List.of(listener1, listener2),
      modulePermissionsService, tenantService, egressLookup);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(appManagerService, requestHandler1, requestHandler2, listener1, listener2,
      modulePermissionsService, tenantService, egressLookup);
  }

  @Test
  void constructor_negative_noRequestHandlers() {
    var listeners = List.of(listener1, listener2);
    var handlers = List.<Handler<RoutingContext>>of();

    Assertions.assertThatThrownBy(() -> new RoutingService(appManagerService, handlers,
        listeners, modulePermissionsService, tenantService, egressLookup))
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
    // init must NOT call loadEgressBootstrapPerApplication itself; SidecarInitializer orchestrates that
    verifyNoInteractions(tenantService, egressLookup);
    verify(appManagerService).getModuleBootstrap();
  }

  @Test
  void loadEgressBootstrapPerApplication_positive_multipleApplications() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;
    var appId1 = TestConstants.APPLICATION_ID;
    var appId2 = "application-1.0.0";

    when(appManagerService.getModuleBootstrap(appId1)).thenReturn(succeededFuture(bootstrap));
    when(appManagerService.getModuleBootstrap(appId2)).thenReturn(succeededFuture(bootstrap));
    when(tenantService.getAllApplicationIds()).thenReturn(Set.of(appId1, appId2));

    routingService.loadEgressBootstrapPerApplication();

    verify(tenantService).getAllApplicationIds();
    verify(appManagerService).getModuleBootstrap(appId1);
    verify(appManagerService).getModuleBootstrap(appId2);
    verify(egressLookup).onApplicationBootstrap(appId1, bootstrap.getRequiredModules());
    verify(egressLookup).onApplicationBootstrap(appId2, bootstrap.getRequiredModules());
  }

  @Test
  void loadEgressBootstrapPerApplication_positive_noApplications() {
    when(tenantService.getAllApplicationIds()).thenReturn(Set.of());

    routingService.loadEgressBootstrapPerApplication();

    verify(tenantService).getAllApplicationIds();
    verify(egressLookup, never()).onApplicationBootstrap(any(), any());
    verifyNoInteractions(appManagerService);
  }

  @Test
  void init_negative() {
    when(appManagerService.getModuleBootstrap()).thenReturn(failedFuture(new NotFoundException("not found")));

    routingService.init(router);

    verifyNoInteractions(router, tenantService, egressLookup);
  }

  @Test
  void loadEgressBootstrapPerApplication_positive_applicationBootstrapFails_logsWarningAndContinues() {
    var appId = TestConstants.APPLICATION_ID;

    when(appManagerService.getModuleBootstrap(appId))
      .thenReturn(failedFuture(new RuntimeException("AM unreachable")));
    when(tenantService.getAllApplicationIds()).thenReturn(Set.of(appId));

    routingService.loadEgressBootstrapPerApplication();

    verify(tenantService).getAllApplicationIds();
    verify(appManagerService).getModuleBootstrap(appId);
    verify(egressLookup, never()).onApplicationBootstrap(any(), any());
  }

  @Test
  void updateModuleRoutes_positive_updateIngressRoutes() {
    var bootstrap = TestConstants.MODULE_BOOTSTRAP;

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));
    when(router.route("/*")).thenReturn(route);

    routingService.init(router);
    reset(listener1, listener2, router, route, tenantService, egressLookup, appManagerService);

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));

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
    reset(listener1, listener2, router, route, tenantService, egressLookup, appManagerService);

    when(appManagerService.getModuleBootstrap()).thenReturn(succeededFuture(bootstrap));

    var listenersOrder = inOrder(listener1, listener2);

    routingService.updateModuleRoutes("mod-bar-0.5.1");

    listenersOrder.verify(listener1).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), UPDATE);
    listenersOrder.verify(listener2).onRequiredModulesBootstrap(bootstrap.getRequiredModules(), UPDATE);
    verify(modulePermissionsService).putPermissions(anySet());
  }

  @Test
  void updateModuleRoutes_negative_moduleNotFound() {
    routingService.updateModuleRoutes("unknown_module");
    verifyNoInteractions(appManagerService, listener1, listener2, router, route, tenantService, egressLookup);
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
