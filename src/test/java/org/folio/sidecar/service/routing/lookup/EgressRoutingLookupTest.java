package org.folio.sidecar.service.routing.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
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
