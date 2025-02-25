package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static java.lang.String.format;
import static java.util.List.of;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
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
import java.util.List;
import java.util.stream.Stream;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EgressRoutingLookupTest {

  private static final Module MOD_BAR = new Module("mod-bar-0.5.1", "http://mod-bar:8081");
  private static final Module MOD_BAZ = new Module("mod-baz-0.5.1", "http://mod-baz:8081");

  private final EgressRoutingLookup egressLookup = new EgressRoutingLookup();

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("egressRequestDataProvider")
  void lookupForEgressRequest_parameterized(HttpMethod method, String path, ScRoutingEntry expected) {
    egressLookup.onRequiredModulesBootstrap(MODULE_BOOTSTRAP_EGRESS.getRequiredModules(), INIT);

    var actual = egressLookup.lookupRoute(path, routingContext(method));

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(ofNullable(expected));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1} ({2})")
  @MethodSource("egressRequestMultiDataProvider")
  void lookupForEgressRequestMulti_parameterized(HttpMethod method, String path, Module module,
    ScRoutingEntry expected) {
    egressLookup.onRequiredModulesBootstrap(MODULE_BOOTSTRAP_EGRESS.getRequiredModules(), INIT);
    var rc = routingContext(method);
    if (module != null) {
      lenient().when(rc.request().getHeader(OkapiHeaders.MODULE_ID)).thenReturn(module.id());
    }

    var actual = egressLookup.lookupRoute(path, rc);

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(ofNullable(expected));
  }

  static Stream<Arguments> egressRequestDataProvider() {
    String id = "00000000-0000-0000-0000-000000000000";
    return Stream.of(
      arguments(GET, null, null),
      arguments(POST, "/bar/entities", scRoutingEntry("bar", "/bar/entities", of(POST), of("item.post"))),
      arguments(GET, format("/bar/entities/%s", id),
        scRoutingEntry("bar", "/bar/entities/{id}", of(GET), of("item.get"))),
      arguments(PUT, format("/bar/entities/%s", id),
        scRoutingEntry("bar", "/bar/entities/{id}", of(PUT), of("item.put"))),
      arguments(POST, format("/bar/entities/%s", id), null),
      arguments(PATCH, format("/bar/entities/%s", id), null),
      arguments(DELETE, format("/bar/entities/%s", id),
        scRoutingEntry("bar", "/bar/entities/{id}", of(DELETE), of("item.delete")))
    );
  }

  static Stream<Arguments> egressRequestMultiDataProvider() {
    String id = "00000000-0000-0000-0000-000000000000";
    return Stream.of(
      arguments(GET, null, MOD_BAZ, null),
      arguments(POST, "/bam/multi/entities", MOD_BAR,
        scRoutingEntryMulti(MOD_BAR, "bam", "/bam/multi/entities", of(GET, POST), of("multi.collection"))),
      arguments(GET, "/bam/multi/entities", MOD_BAZ,
        scRoutingEntryMulti(MOD_BAZ, "bam", "/bam/multi/entities", of(GET, POST), of("multi.collection"))),
      arguments(GET, format("/bam/multi/entities/%s", id), MOD_BAZ,
        scRoutingEntryMulti(MOD_BAZ, "bam", "/bam/multi/entities/{id}", of(GET), of("multi.item.get"))),
      arguments(GET, format("/bam/multi/entities/%s", id), MOD_BAR,
        scRoutingEntryMulti(MOD_BAR, "bam", "/bam/multi/entities/{id}", of(GET), of("multi.item.get"))),

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

  private static RoutingContext routingContext(HttpMethod method) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(request);
    when(request.method()).thenReturn(method);
    return routingContext;
  }

  private record Module(String id, String url) {
  }
}
