package org.folio.sidecar.service.routing.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.am.model.ModuleBootstrapInterface;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EgressRoutingLookupTest {

  @Mock RoutingContext routingContext;
  @Mock HttpServerRequest request;

  private EgressRoutingLookup lookup;

  @BeforeEach
  void setUp() {
    lookup = new EgressRoutingLookup();
  }

  @Test
  void lookupRoute_selectsTableByTenant() {
    lookup.updateTenantRoutes("tenant1", List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/entries")));

    mockRequest("tenant1", HttpMethod.GET);
    var entry = lookup.lookupRoute("/foo/entries", routingContext).result();
    assertThat(entry).isPresent();
    assertThat(entry.get().getModuleId()).isEqualTo("mod-foo-1.0.0");
  }

  @Test
  void lookupRoute_returnsEmptyForUnknownTenant() {
    lookup.updateTenantRoutes("tenant1", List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/entries")));

    mockRequest("tenant2", HttpMethod.GET);
    assertThat(lookup.lookupRoute("/foo/entries", routingContext).result()).isEmpty();
  }

  @Test
  void removeTenantRoutes_dropsTable() {
    lookup.updateTenantRoutes("tenant1", List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/entries")));
    lookup.removeTenantRoutes("tenant1");

    mockRequest("tenant1", HttpMethod.GET);
    assertThat(lookup.lookupRoute("/foo/entries", routingContext).result()).isEmpty();
  }

  @Test
  void hasTenant_returnsTrueAfterUpdate() {
    assertThat(lookup.hasTenant("tenant1")).isFalse();
    lookup.updateTenantRoutes("tenant1", List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/entries")));
    assertThat(lookup.hasTenant("tenant1")).isTrue();
  }

  @Test
  void hasTenant_returnsFalseAfterRemove() {
    lookup.updateTenantRoutes("tenant1", List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/entries")));
    lookup.removeTenantRoutes("tenant1");
    assertThat(lookup.hasTenant("tenant1")).isFalse();
  }

  @Test
  void lookupRoute_matchesPathTemplate() {
    lookup.updateTenantRoutes("tenant1",
      List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/{id}", "GET")));

    mockRequest("tenant1", HttpMethod.GET);
    var entry = lookup.lookupRoute("/foo/42", routingContext).result();
    assertThat(entry).isPresent();
    assertThat(entry.get().getModuleId()).isEqualTo("mod-foo-1.0.0");
  }

  @Test
  void lookupRoute_returnsEmptyForMethodMismatch() {
    lookup.updateTenantRoutes("tenant1",
      List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/entries", "GET")));

    mockRequest("tenant1", HttpMethod.POST);
    assertThat(lookup.lookupRoute("/foo/entries", routingContext).result()).isEmpty();
  }

  @Test
  void lookupRoute_fallsBackToStaticWhenNoTenantTable() {
    lookup.onRequiredModulesBootstrap(
      List.of(discovery("mod-static-1.0.0", "http://static:8081", "/static/res")), INIT);

    mockRequest("any-tenant", HttpMethod.GET);
    var entry = lookup.lookupRoute("/static/res", routingContext).result();
    assertThat(entry).isPresent();
    assertThat(entry.get().getModuleId()).isEqualTo("mod-static-1.0.0");
  }

  @Test
  void lookupRoute_prefersTenantTableOverStatic() {
    lookup.onRequiredModulesBootstrap(
      List.of(discovery("mod-static-1.0.0", "http://static:8081", "/res")), INIT);
    lookup.updateTenantRoutes("tenant1",
      List.of(discovery("mod-tenant-1.0.0", "http://tenant:8081", "/res")));

    mockRequest("tenant1", HttpMethod.GET);
    var entry = lookup.lookupRoute("/res", routingContext).result();
    assertThat(entry).isPresent();
    assertThat(entry.get().getModuleId()).isEqualTo("mod-tenant-1.0.0");
  }

  @Test
  void onRequiredModulesBootstrap_updatesStaticCache() {
    lookup.onRequiredModulesBootstrap(
      List.of(discovery("mod-v1-1.0.0", "http://v1:8081", "/api/v1")), INIT);
    lookup.onRequiredModulesBootstrap(
      List.of(discovery("mod-v2-2.0.0", "http://v2:8081", "/api/v2")), UPDATE);

    mockRequest("any-tenant", HttpMethod.GET);
    assertThat(lookup.lookupRoute("/api/v1", routingContext).result()).isEmpty();
    var entry = lookup.lookupRoute("/api/v2", routingContext).result();
    assertThat(entry).isPresent();
    assertThat(entry.get().getModuleId()).isEqualTo("mod-v2-2.0.0");
  }

  @Test
  void lookupRoute_strictPriorityWithDifferentModuleVersions() {
    // static (global) cache points the shared path at the NEWER provider version
    lookup.onRequiredModulesBootstrap(
      List.of(discovery("mod-provider-2.1.8", "http://provider-new:8081", "/egress/app-platform")),
      INIT);
    // tenant1's scoped table points the SAME path at the OLDER provider version
    lookup.updateTenantRoutes("tenant1",
      List.of(discovery("mod-provider-2.0.53", "http://provider-old:8081", "/egress/app-platform")));

    // tenant1 has a scoped table -> must get the OLDER (scoped) version, never the globally-newer one
    mockRequest("tenant1", HttpMethod.GET);
    var scoped = lookup.lookupRoute("/egress/app-platform", routingContext).result();
    assertThat(scoped).isPresent();
    assertThat(scoped.get().getModuleId()).isEqualTo("mod-provider-2.0.53");

    // tenant2 has NO scoped table -> falls back to the static cache (newer version)
    mockRequest("tenant2", HttpMethod.GET);
    var fallback = lookup.lookupRoute("/egress/app-platform", routingContext).result();
    assertThat(fallback).isPresent();
    assertThat(fallback.get().getModuleId()).isEqualTo("mod-provider-2.1.8");

    // after tenant1's scoped table is removed, tenant1 also falls back to static
    lookup.removeTenantRoutes("tenant1");
    mockRequest("tenant1", HttpMethod.GET);
    var afterRemove = lookup.lookupRoute("/egress/app-platform", routingContext).result();
    assertThat(afterRemove).isPresent();
    assertThat(afterRemove.get().getModuleId()).isEqualTo("mod-provider-2.1.8");
  }

  private void mockRequest(String tenant, HttpMethod method) {
    when(routingContext.request()).thenReturn(request);
    when(request.getHeader(TENANT)).thenReturn(tenant);
    when(request.method()).thenReturn(method);
  }

  private static ModuleBootstrapDiscovery discovery(String moduleId, String location, String path) {
    return discovery(moduleId, location, path, "GET");
  }

  private static ModuleBootstrapDiscovery discovery(String moduleId, String location, String path, String method) {
    var endpoint = new ModuleBootstrapEndpoint(path, method);
    var iface = new ModuleBootstrapInterface();
    iface.setId("foo");
    iface.setEndpoints(List.of(endpoint));
    var discovery = new ModuleBootstrapDiscovery();
    discovery.setModuleId(moduleId);
    discovery.setLocation(location);
    discovery.setInterfaces(List.of(iface));
    return discovery;
  }
}
