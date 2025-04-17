package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.OPTIONS;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestValues;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IngressRoutingLookupTest {

  private final IngressRoutingLookup ingressLookup = new IngressRoutingLookup();

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("ingressRequestDataProvider")
  @DisplayName("lookupForIngressRequest_parameterized")
  void lookupForIngressRequest_parameterized(HttpMethod method, String path, ScRoutingEntry expected) {
    ingressLookup.onModuleBootstrap(TestConstants.MODULE_BOOTSTRAP.getModule(), INIT);

    var actual = ingressLookup.lookupRoute(path, routingContext(method));

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(ofNullable(expected));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("ingressRequestMultiDataProvider")
  @DisplayName("lookupForIngressRequestMulti_parameterized")
  void lookupForIngressRequestMulti_parameterized(HttpMethod method, String path, ScRoutingEntry expected) {
    ingressLookup.onModuleBootstrap(TestConstants.MODULE_BOOTSTRAP_MULTI.getModule(), INIT);
    var actual = ingressLookup.lookupRoute(path, routingContext(method, "mod-target-1.0.0"));

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(ofNullable(expected));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("ingressRequestMultiMatchDataProvider")
  @DisplayName("lookupForIngressRequestMulti_parameterized")
  void lookupForIngressRequestMultiMatch_parameterized(HttpMethod method, String path, ScRoutingEntry expected) {
    ingressLookup.onModuleBootstrap(TestConstants.MODULE_BOOTSTRAP_MULTI.getModule(), INIT);
    var actual = ingressLookup.lookupRoute(path, routingContext(method, "mod-foo-0.2.1"));

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(ofNullable(expected));
  }

  @Test
  void lookupForIngressRequest_positive_emptyModuleDescriptor() {
    ingressLookup.onModuleBootstrap(new ModuleBootstrapDiscovery(), INIT);

    var actual = ingressLookup.lookupRoute("/foo/entities", routingContext(GET));

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEmpty();
  }

  private static Stream<Arguments> ingressRequestDataProvider() {
    var id1 = "00000000-0000-0000-0000-000000000000";
    var id2 = "ffffffff-ffff-ffff-ffff-ffffffffffff";
    return Stream.of(
      arguments(GET, null, null),
      arguments(GET, "/foo/entities", TestValues.scRoutingEntry("foo", "/foo/entities", GET, POST)),
      arguments(POST, "/foo/entities", TestValues.scRoutingEntry("foo", "/foo/entities", GET, POST)),
      arguments(null, "/foo/entities", TestValues.scRoutingEntry("foo", "/foo/entities", GET, POST)),
      arguments(DELETE, "/foo/entities", null),
      arguments(GET, "/foo/entities/", null),
      arguments(GET, "/unknown/entities", null),
      arguments(OPTIONS, "/unknown/entities", TestValues.scRoutingEntry("other-legacy", null, OPTIONS)),

      arguments(GET, format("/foo/entities/%s", id1), TestValues.scRoutingEntry("foo", "/foo/entities/{id}", GET)),
      arguments(PUT, format("/foo/entities/%s", id1), TestValues.scRoutingEntry("foo", "/foo/entities/{id}", PUT)),
      arguments(POST, format("/foo/entities/%s", id1), null),
      arguments(PATCH, format("/foo/entities/%s", id1), null),
      arguments(OPTIONS, format("/foo/entities/%s", id1), TestValues.scRoutingEntry("other-legacy", null, OPTIONS)),
      arguments(DELETE, format("/foo/entities/%s", id1),
        TestValues.scRoutingEntry("foo", "/foo/entities/{id}", DELETE)),

      arguments(PUT, format("/foo/%s/entities", id1),
        TestValues.scRoutingEntry("foo", "/foo/{id}/entities", PUT, PATCH)),
      arguments(PATCH, format("/foo/%s/entities", id1),
        TestValues.scRoutingEntry("foo", "/foo/{id}/entities", PUT, PATCH)),

      arguments(GET, format("/foo/%s/sub-entities/%s", id1, id2),
        TestValues.scRoutingEntry("foo", "/foo/{fooId}/sub-entities/{subEntityId}", GET, PUT)),

      arguments(PATCH, format("/foo/%s/sub-entities-2/%s", id1, id2),
        TestValues.scRoutingEntry("foo", "/foo/{foo-id}/sub-entities-2/{sub-entity-id}", PATCH)),

      arguments(GET, "/foo2/entities", TestValues.scRoutingEntry("foo2", "/foo2*", "*")),
      arguments(POST, "/foo2/entities", TestValues.scRoutingEntry("foo2", "/foo2*", "*")),
      arguments(PUT, format("/foo2/entities/%s", id1), TestValues.scRoutingEntry("foo2", "/foo2*", "*")),
      arguments(POST, format("/foo2/%s/entities", id1), TestValues.scRoutingEntry("foo2", "/foo2*", "*")),

      // legacy stuff
      arguments(GET, "/foo3/values",
        TestValues.scRoutingEntry("foo3", TestValues.routingEntryWithPath("/foo3/values", GET))),
      arguments(POST, "/foo3/values", null),
      arguments(POST, "/foo3/samples", null),
      arguments(GET, "{/", null),
      arguments(GET, "/{}", TestValues.scRoutingEntry("other-legacy", "/{id}", GET)),
      arguments(GET, id1, null),
      arguments(GET, format("%s/", id1), null),
      arguments(GET, format("/%s/", id1), null),
      arguments(GET, format("/%s", id1), TestValues.scRoutingEntry("other-legacy", "/{id}", GET)),
      arguments(GET, format("/%s//%s", id1, id2), TestValues.scRoutingEntry("other-legacy", "/{id1}/*/{id2}", GET)),
      arguments(GET, format("/foo/%s/entities", id1), TestValues.scRoutingEntry("other-legacy", "/{id1}/*/{id2}", GET)),

      // tenant API
      arguments(POST, "/_/tenant", TestValues.scRoutingEntrySysInterface("_tenant", "/_/tenant", POST)),
      arguments(GET, "/_/tenant", null),
      arguments(POST, format("/_/tenant/%s", id2), null),
      arguments(GET, format("/_/tenant/%s", id2),
        TestValues.scRoutingEntrySysInterface("_tenant", "/_/tenant/{id}", GET, DELETE)),
      arguments(DELETE, format("/_/tenant/%s", id2),
        TestValues.scRoutingEntrySysInterface("_tenant", "/_/tenant/{id}", GET, DELETE))
    );
  }

  private static Stream<Arguments> ingressRequestMultiMatchDataProvider() {
    return Stream.of(
      arguments(GET, "/foo/entities", TestValues.scRoutingEntryMultiInterface("foo", "/foo/entities", GET, POST)),
      arguments(POST, "/foo/entities", TestValues.scRoutingEntryMultiInterface("foo", "/foo/entities", GET, POST)),
      arguments(null, "/foo/entities", TestValues.scRoutingEntryMultiInterface("foo", "/foo/entities", GET, POST))
    );
  }

  private static Stream<Arguments> ingressRequestMultiDataProvider() {
    return Stream.of(
      arguments(GET, "/foo/entities", null),
      arguments(POST, "/foo/entities", null),
      arguments(null, "/foo/entities", null)
    );
  }

  private static RoutingContext routingContext(HttpMethod method) {
    return routingContext(method, null);
  }

  private static RoutingContext routingContext(HttpMethod method, String targetModule) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(request);
    when(request.method()).thenReturn(method);
    if (StringUtils.isNoneBlank(targetModule)) {
      when(request.getHeader(OkapiHeaders.MODULE_ID)).thenReturn(targetModule);
    }
    return routingContext;
  }
}
