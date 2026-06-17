package org.folio.sidecar.service.routing.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.MODULE_ID;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.utils.RoutingUtils.MULTIPLE_INTERFACE_TYPE;
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
    lookup.updateTenantRoutes("tenant2", List.of(discovery("mod-bar-1.0.0", "http://bar:8081", "/bar/entries")));

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
  void lookupRoute_returnsEmptyWhenNoTenantTable() {
    mockRequest("any-tenant", HttpMethod.GET);
    assertThat(lookup.lookupRoute("/foo/entries", routingContext).result()).isEmpty();
  }

  @Test
  void lookupRoute_returnsEmptyWhenPathDoesNotMatch() {
    lookup.updateTenantRoutes("tenant1", List.of(discovery("mod-foo-1.0.0", "http://foo:8081", "/foo/entries")));

    mockRequest("tenant1", HttpMethod.GET);
    assertThat(lookup.lookupRoute("/unknown/path", routingContext).result()).isEmpty();
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
  void lookupRoute_scopedTablesAreIsolatedAcrossModuleVersions() {
    // tenant1's scoped table points the shared path at the OLDER provider version
    lookup.updateTenantRoutes("tenant1",
      List.of(discovery("mod-provider-2.0.53", "http://provider-old:8081", "/egress/app-platform")));
    // tenant2's scoped table points the SAME path at the NEWER provider version
    lookup.updateTenantRoutes("tenant2",
      List.of(discovery("mod-provider-2.1.8", "http://provider-new:8081", "/egress/app-platform")));

    // each tenant resolves strictly from its own scoped table
    mockRequest("tenant1", HttpMethod.GET);
    var scopedOld = lookup.lookupRoute("/egress/app-platform", routingContext).result();
    assertThat(scopedOld).isPresent();
    assertThat(scopedOld.get().getModuleId()).isEqualTo("mod-provider-2.0.53");

    mockRequest("tenant2", HttpMethod.GET);
    var scopedNew = lookup.lookupRoute("/egress/app-platform", routingContext).result();
    assertThat(scopedNew).isPresent();
    assertThat(scopedNew.get().getModuleId()).isEqualTo("mod-provider-2.1.8");

    // after tenant1's scoped table is removed, tenant1 no longer resolves the route
    lookup.removeTenantRoutes("tenant1");
    mockRequest("tenant1", HttpMethod.GET);
    assertThat(lookup.lookupRoute("/egress/app-platform", routingContext).result()).isEmpty();
  }

  @Test
  void lookupRoute_multiInterface_resolvesFirstProviderByModuleIdHeader() {
    // tenant1's scoped table has two providers that expose the same path through a multiple-type interface,
    // so egress disambiguates between them using the X-Okapi-Module-Id header
    lookup.updateTenantRoutes("tenant1", multiInterfaceProviders());

    mockMultiRequest("tenant1", HttpMethod.GET, "mod-bar-1.0.0");
    var entry = lookup.lookupRoute("/shared/entities", routingContext).result();

    assertThat(entry).isPresent();
    assertThat(entry.get().getModuleId()).isEqualTo("mod-bar-1.0.0");
  }

  @Test
  void lookupRoute_multiInterface_resolvesSecondProviderByModuleIdHeader() {
    lookup.updateTenantRoutes("tenant1", multiInterfaceProviders());

    mockMultiRequest("tenant1", HttpMethod.GET, "mod-baz-1.0.0");
    var entry = lookup.lookupRoute("/shared/entities", routingContext).result();

    assertThat(entry).isPresent();
    assertThat(entry.get().getModuleId()).isEqualTo("mod-baz-1.0.0");
  }

  @Test
  void lookupRoute_multiInterface_returnsEmptyWhenModuleIdHeaderAbsent() {
    lookup.updateTenantRoutes("tenant1", multiInterfaceProviders());

    // No X-Okapi-Module-Id header: a "multiple" interface type cannot be disambiguated, so nothing matches.
    mockRequest("tenant1", HttpMethod.GET);
    assertThat(lookup.lookupRoute("/shared/entities", routingContext).result()).isEmpty();
  }

  private void mockRequest(String tenant, HttpMethod method) {
    when(routingContext.request()).thenReturn(request);
    when(request.getHeader(TENANT)).thenReturn(tenant);
    when(request.method()).thenReturn(method);
  }

  private void mockMultiRequest(String tenant, HttpMethod method, String moduleId) {
    mockRequest(tenant, method);
    when(request.getHeader(MODULE_ID)).thenReturn(moduleId);
  }

  private static List<ModuleBootstrapDiscovery> multiInterfaceProviders() {
    return List.of(
      multiInterfaceDiscovery("mod-bar-1.0.0", "http://bar:8081", "/shared/entities"),
      multiInterfaceDiscovery("mod-baz-1.0.0", "http://baz:8081", "/shared/entities"));
  }

  private static ModuleBootstrapDiscovery multiInterfaceDiscovery(String moduleId, String location, String path) {
    var endpoint = new ModuleBootstrapEndpoint(path, "GET");
    var iface = new ModuleBootstrapInterface();
    iface.setId("shared");
    iface.setInterfaceType(MULTIPLE_INTERFACE_TYPE);
    iface.setEndpoints(List.of(endpoint));
    var discovery = new ModuleBootstrapDiscovery();
    discovery.setModuleId(moduleId);
    discovery.setLocation(location);
    discovery.setInterfaces(List.of(iface));
    return discovery;
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
