package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP_EGRESS;
import static org.folio.sidecar.support.TestValues.routingEntryWithPerms;
import static org.folio.sidecar.utils.CollectionUtils.safeList;
import static org.folio.sidecar.utils.RoutingUtils.MULTIPLE_INTERFACE_TYPE;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.am.model.ModuleBootstrapInterface;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.TenantService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EgressRoutingLookupTest {

  private static final Module MOD_BAR = new Module("mod-bar-0.5.1", "http://mod-bar:8081");
  private static final Module MOD_BAZ = new Module("mod-baz-0.5.1", "http://mod-baz:8081");

  @Mock
  private TenantService tenantService;

  private EgressRoutingLookup egressLookup;

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("egressRequestDataProvider")
  void lookupForEgressRequest_parameterized(HttpMethod method, String path, ScRoutingEntry expected) {
    egressLookup = new EgressRoutingLookup(tenantService);
    when(tenantService.getApplicationIds("testtenant")).thenReturn(Set.of("application-0.0.1"));
    egressLookup.onApplicationBootstrap("application-0.0.1", MODULE_BOOTSTRAP_EGRESS.getRequiredModules());

    var actual = egressLookup.lookupRoute(path, routingContext(method, "testtenant"));

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(ofNullable(expected));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1} ({2})")
  @MethodSource("egressRequestMultiDataProvider")
  void lookupForEgressRequestMulti_parameterized(HttpMethod method, String path, Module module,
    ScRoutingEntry expected) {
    egressLookup = new EgressRoutingLookup(tenantService);
    when(tenantService.getApplicationIds("testtenant")).thenReturn(Set.of("application-0.0.1"));
    egressLookup.onApplicationBootstrap("application-0.0.1", MODULE_BOOTSTRAP_EGRESS.getRequiredModules());
    var rc = routingContext(method, "testtenant");
    if (module != null) {
      lenient().when(rc.request().getHeader(OkapiHeaders.MODULE_ID)).thenReturn(module.id());
    }

    var actual = egressLookup.lookupRoute(path, rc);

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(ofNullable(expected));
  }

  @Test
  void lookupRoute_overlappingPaths_sortedAppIdWins_deterministic() {
    egressLookup = new EgressRoutingLookup(tenantService);

    // Two apps both expose the same path/method
    var app1Discovery = buildDiscovery("mod-alpha-1.0.0", "http://mod-alpha:8081", "app-alpha-1.0.0",
      "common", null, "/common/items", List.of("GET"));
    var app2Discovery = buildDiscovery("mod-beta-1.0.0", "http://mod-beta:8081", "app-beta-1.0.0",
      "common", null, "/common/items", List.of("GET"));

    egressLookup.onApplicationBootstrap("app-alpha-1.0.0", List.of(app1Discovery));
    egressLookup.onApplicationBootstrap("app-beta-1.0.0", List.of(app2Discovery));

    // Tenant entitled to both apps
    when(tenantService.getApplicationIds("tenant-x"))
      .thenReturn(Set.of("app-alpha-1.0.0", "app-beta-1.0.0"));

    var result1 = egressLookup.lookupRoute("/common/items", routingContext(GET, "tenant-x"));
    var result2 = egressLookup.lookupRoute("/common/items", routingContext(GET, "tenant-x"));

    // Both calls must return a result and it must be the same module (sorted: app-alpha before app-beta)
    assertThat(result1.result()).isPresent();
    assertThat(result2.result()).isPresent();
    assertThat(result1.result().get().getModuleId()).isEqualTo(result2.result().get().getModuleId());
    assertThat(result1.result().get().getModuleId()).isEqualTo("mod-alpha-1.0.0");
  }

  @Test
  void lookupRoute_perApplicationIsolation_tenantSeesOnlyItsAppRoutes() {
    egressLookup = new EgressRoutingLookup(tenantService);

    var barDiscovery = buildDiscovery("mod-bar-0.5.1", "http://mod-bar:8081", "app-1",
      "bar", null, "/bar/entities", List.of("POST"));
    var bazDiscovery = buildDiscovery("mod-baz-0.5.1", "http://mod-baz:8081", "app-2",
      "baz", null, "/baz/items", List.of("GET"));

    egressLookup.onApplicationBootstrap("app-1", List.of(barDiscovery));
    egressLookup.onApplicationBootstrap("app-2", List.of(bazDiscovery));

    // tenant-a is only entitled to app-1 → sees bar route, not baz
    when(tenantService.getApplicationIds("tenant-a")).thenReturn(Set.of("app-1"));
    var rcA = routingContext(POST, "tenant-a");
    var resultA = egressLookup.lookupRoute("/bar/entities", rcA);
    assertThat(resultA.succeeded()).isTrue();
    assertThat(resultA.result()).isPresent();
    assertThat(resultA.result().get().getModuleId()).isEqualTo("mod-bar-0.5.1");

    var missingRouteA = egressLookup.lookupRoute("/baz/items", rcA);
    assertThat(missingRouteA.succeeded()).isTrue();
    assertThat(missingRouteA.result()).isEmpty();

    // tenant-b is only entitled to app-2 → sees baz route, not bar
    when(tenantService.getApplicationIds("tenant-b")).thenReturn(Set.of("app-2"));
    var rcB = routingContext(GET, "tenant-b");
    var resultB = egressLookup.lookupRoute("/baz/items", rcB);
    assertThat(resultB.succeeded()).isTrue();
    assertThat(resultB.result()).isPresent();
    assertThat(resultB.result().get().getModuleId()).isEqualTo("mod-baz-0.5.1");

    var missingRouteB = egressLookup.lookupRoute("/bar/entities", rcB);
    assertThat(missingRouteB.succeeded()).isTrue();
    assertThat(missingRouteB.result()).isEmpty();
  }

  @Test
  void onRequiredModulesBootstrap_null_noOp() {
    egressLookup = new EgressRoutingLookup(tenantService);
    egressLookup.onRequiredModulesBootstrap(null, INIT);

    when(tenantService.getApplicationIds("tenant-a")).thenReturn(Set.of("app-1"));
    var result = egressLookup.lookupRoute("/any/path", routingContext(GET, "tenant-a"));
    assertThat(result.result()).isEmpty();
  }

  @Test
  void onRequiredModulesBootstrap_empty_noOp() {
    egressLookup = new EgressRoutingLookup(tenantService);
    egressLookup.onRequiredModulesBootstrap(Collections.emptyList(), INIT);

    when(tenantService.getApplicationIds("tenant-a")).thenReturn(Set.of("app-1"));
    var result = egressLookup.lookupRoute("/any/path", routingContext(GET, "tenant-a"));
    assertThat(result.result()).isEmpty();
  }

  @Test
  void onRequiredModulesBootstrap_update_populatesCache() {
    egressLookup = new EgressRoutingLookup(tenantService);
    var discovery = buildDiscovery("mod-bar-0.5.1", "http://mod-bar:8081", "app-1",
      "bar", null, "/bar/entities", List.of("POST"));

    egressLookup.onRequiredModulesBootstrap(List.of(discovery), UPDATE);

    when(tenantService.getApplicationIds("tenant-a")).thenReturn(Set.of("app-1"));
    var result = egressLookup.lookupRoute("/bar/entities", routingContext(POST, "tenant-a"));
    assertThat(result.result()).isPresent();
    assertThat(result.result().get().getModuleId()).isEqualTo("mod-bar-0.5.1");
  }

  @Test
  void lookupRoute_afterApplicationRevoked_routesAreGone() {
    egressLookup = new EgressRoutingLookup(tenantService);

    var barDiscovery = buildDiscovery("mod-bar-0.5.1", "http://mod-bar:8081", "app-1",
      "bar", null, "/bar/entities", List.of("POST"));
    egressLookup.onApplicationBootstrap("app-1", List.of(barDiscovery));

    when(tenantService.getApplicationIds("tenant-a")).thenReturn(Set.of("app-1"));
    var rcA = routingContext(POST, "tenant-a");

    var before = egressLookup.lookupRoute("/bar/entities", rcA);
    assertThat(before.result()).isPresent();

    egressLookup.onApplicationRevoked("app-1");

    // route must be gone even if TenantService still lists the app (the cache entry was removed)
    var after = egressLookup.lookupRoute("/bar/entities", rcA);
    assertThat(after.succeeded()).isTrue();
    assertThat(after.result()).isEmpty();
  }

  static Stream<Arguments> egressRequestDataProvider() {
    String id = "00000000-0000-0000-0000-000000000000";
    return Stream.of(
      arguments(GET, null, null),
      arguments(POST, "/bar/entities", scRoutingEntry("bar", "/bar/entities", List.of(POST), List.of("item.post"))),
      arguments(GET, format("/bar/entities/%s", id),
        scRoutingEntry("bar", "/bar/entities/{id}", List.of(GET), List.of("item.get"))),
      arguments(PUT, format("/bar/entities/%s", id),
        scRoutingEntry("bar", "/bar/entities/{id}", List.of(PUT), List.of("item.put"))),
      arguments(POST, format("/bar/entities/%s", id), null),
      arguments(PATCH, format("/bar/entities/%s", id), null),
      arguments(DELETE, format("/bar/entities/%s", id),
        scRoutingEntry("bar", "/bar/entities/{id}", List.of(DELETE), List.of("item.delete")))
    );
  }

  static Stream<Arguments> egressRequestMultiDataProvider() {
    String id = "00000000-0000-0000-0000-000000000000";
    return Stream.of(
      arguments(GET, null, MOD_BAZ, null),
      arguments(POST, "/bam/multi/entities", MOD_BAR,
        scRoutingEntryMulti(MOD_BAR, "bam", "/bam/multi/entities", List.of(GET, POST), List.of("multi.collection"))),
      arguments(GET, "/bam/multi/entities", MOD_BAZ,
        scRoutingEntryMulti(MOD_BAZ, "bam", "/bam/multi/entities", List.of(GET, POST), List.of("multi.collection"))),
      arguments(GET, format("/bam/multi/entities/%s", id), MOD_BAZ,
        scRoutingEntryMulti(MOD_BAZ, "bam", "/bam/multi/entities/{id}", List.of(GET), List.of("multi.item.get"))),
      arguments(GET, format("/bam/multi/entities/%s", id), MOD_BAR,
        scRoutingEntryMulti(MOD_BAR, "bam", "/bam/multi/entities/{id}", List.of(GET), List.of("multi.item.get"))),

      arguments(GET, format("/bar/entities/%s", id), MOD_BAZ, null),
      arguments(DELETE, "/bam/multi/entities", MOD_BAR, null),
      arguments(POST, "/bam/multi/entities", null, null)
    );
  }

  private static ScRoutingEntry scRoutingEntry(String interfaceId, String pathPattern, List<HttpMethod> httpMethods,
    List<String> requiredPermissions) {
    return scRoutingEntry(MOD_BAR, interfaceId, null, pathPattern, httpMethods, requiredPermissions);
  }

  private static ScRoutingEntry scRoutingEntry(Module module, String interfaceId, String interfaceType,
    String pathPattern, List<HttpMethod> httpMethods, List<String> requiredPermissions) {
    var methods = safeList(httpMethods).stream().map(HttpMethod::name).toList();
    return ScRoutingEntry.of(module.id(), module.url(), interfaceId, interfaceType,
      routingEntryWithPerms(pathPattern, methods, requiredPermissions));
  }

  private static ScRoutingEntry scRoutingEntryMulti(Module module, String interfaceId, String pathPattern,
    List<HttpMethod> httpMethods, List<String> requiredPermissions) {
    return scRoutingEntry(module, interfaceId, MULTIPLE_INTERFACE_TYPE, pathPattern, httpMethods, requiredPermissions);
  }

  private static RoutingContext routingContext(HttpMethod method, String tenant) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(request);
    when(request.method()).thenReturn(method);
    lenient().when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(tenant);
    return routingContext;
  }

  private static ModuleBootstrapDiscovery buildDiscovery(String moduleId, String location, String applicationId,
    String interfaceId, String interfaceType, String pathPattern, List<String> methods) {
    var endpoint = new ModuleBootstrapEndpoint();
    endpoint.setPathPattern(pathPattern);
    endpoint.setMethods(methods.toArray(new String[0]));

    var iface = new ModuleBootstrapInterface();
    iface.setId(interfaceId);
    iface.setInterfaceType(interfaceType);
    iface.setEndpoints(List.of(endpoint));

    var discovery = new ModuleBootstrapDiscovery();
    discovery.setModuleId(moduleId);
    discovery.setLocation(location);
    discovery.setApplicationId(applicationId);
    discovery.setInterfaces(List.of(iface));
    return discovery;
  }

  private record Module(String id, String url) {
  }
}
