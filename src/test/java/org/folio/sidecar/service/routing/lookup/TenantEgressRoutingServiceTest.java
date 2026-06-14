package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.EgressBootstrapResult;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.service.TenantService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantEgressRoutingServiceTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String TENANT = "tenant1";

  @Mock ApplicationManagerService appManagerService;
  @Mock TenantEntitlementService tenantEntitlementService;
  @Mock EgressRoutingLookup egressRoutingLookup;
  @Mock ModuleProperties moduleProperties;
  @Mock TenantService tenantService;

  private TenantEgressRoutingService service;

  @BeforeEach
  void setUp() {
    when(moduleProperties.getId()).thenReturn(MODULE_ID);
    service = new TenantEgressRoutingService(appManagerService, tenantEntitlementService, egressRoutingLookup,
      moduleProperties);
  }

  @Test
  void refreshTenant_moduleActive_loadsAndUpdates() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement("app-1.0.0", List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider("mod-bar-1.0.0"))))));

    var done = service.refreshTenant(TENANT);

    assertSucceeded(done);
    verify(egressRoutingLookup).updateTenantRoutes(eq(TENANT), any());
    verify(egressRoutingLookup, never()).removeTenantRoutes(TENANT);
  }

  @Test
  void refreshTenant_moduleInactive_removesTable() {
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement("app-1.0.0", emptyList()))));

    var done = service.refreshTenant(TENANT);

    assertSucceeded(done);
    verify(egressRoutingLookup).removeTenantRoutes(TENANT);
    verify(appManagerService, never()).getModuleBootstrapEgress(any());
  }

  @Test
  void refreshTenant_entitleThenRevoke_lastWins() {
    var slowEntitle = Promise.<List<Entitlement>>promise();
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(slowEntitle.future())
      .thenReturn(succeededFuture(List.of(entitlement("app-1.0.0", emptyList()))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.of(found(List.of(provider("mod-bar-1.0.0"))))));

    var entitle = service.refreshTenant(TENANT);
    var revoke = service.refreshTenant(TENANT);

    slowEntitle.complete(List.of(entitlement("app-1.0.0", List.of(MODULE_ID))));

    assertSucceeded(entitle);
    assertSucceeded(revoke);

    var inOrder = org.mockito.Mockito.inOrder(egressRoutingLookup);
    inOrder.verify(egressRoutingLookup).updateTenantRoutes(eq(TENANT), any());
    inOrder.verify(egressRoutingLookup).removeTenantRoutes(TENANT);
  }

  @Test
  void init_bootstrapNetworkError_failsStartup() {
    service.setTenantService(tenantService);
    when(tenantService.getEnabledTenants()).thenReturn(succeededFuture(Set.of(TENANT)));
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement("app-1.0.0", List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(Future.failedFuture(new RuntimeException("connection reset")));

    var done = service.init();

    Assertions.assertThat(done.failed()).isTrue();
    Assertions.assertThat(done.cause()).hasMessageContaining("connection reset");
    verify(egressRoutingLookup, never()).updateTenantRoutes(eq(TENANT), any());
  }

  @Test
  void init_bootstrap404_startsWithoutFailing() {
    service.setTenantService(tenantService);
    when(tenantService.getEnabledTenants()).thenReturn(succeededFuture(Set.of(TENANT)));
    when(tenantEntitlementService.getAllTenantEntitlements(eq(TENANT), anyBoolean()))
      .thenReturn(succeededFuture(List.of(entitlement("app-1.0.0", List.of(MODULE_ID)))));
    when(appManagerService.getModuleBootstrapEgress(any()))
      .thenReturn(succeededFuture(Optional.empty()));

    var done = service.init();

    assertSucceeded(done);
    verify(egressRoutingLookup, never()).updateTenantRoutes(eq(TENANT), any());
  }

  private static void assertSucceeded(Future<Void> future) {
    org.assertj.core.api.Assertions.assertThat(future.succeeded()).isTrue();
  }

  private static Entitlement entitlement(String applicationId, List<String> modules) {
    return Entitlement.of(applicationId, "t-id", modules);
  }

  private static EgressBootstrapResult found(List<ModuleBootstrapDiscovery> required) {
    var bootstrap = new ModuleBootstrap();
    bootstrap.setRequiredModules(required);
    var result = new EgressBootstrapResult();
    result.setFound(true);
    result.setBootstrap(bootstrap);
    return result;
  }

  private static ModuleBootstrapDiscovery provider(String moduleId) {
    var discovery = new ModuleBootstrapDiscovery();
    discovery.setModuleId(moduleId);
    return discovery;
  }
}
