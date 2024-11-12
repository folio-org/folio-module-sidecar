package org.folio.sidecar.service.routing;

import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.OPTIONS;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.am.ApplicationManagerClient;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestValues;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RequestMatchingServiceTest {

  private RequestMatchingService requestMatchingService;
  @Mock private PathProcessor pathProcessor;
  @Mock private SidecarProperties sidecarProperties;
  @Mock private ApplicationManagerClient appManagerClient;

  @BeforeEach
  void setUp() {
    when(sidecarProperties.getUnknownRequestsDestination()).thenReturn(TestConstants.GATEWAY_URL);
    requestMatchingService = new RequestMatchingService(pathProcessor, sidecarProperties);
    requestMatchingService.bootstrapModule(TestConstants.MODULE_BOOTSTRAP);
  }

  @AfterEach
  void tearDown() {
    requestMatchingService.ingressRequestCache.clear();
    requestMatchingService.egressRequestCache.clear();
    verifyNoMoreInteractions(appManagerClient);
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("ingressRequestDataProvider")
  @DisplayName("lookupForIngressRequest_parameterized")
  void lookupForIngressRequest_parameterized(HttpMethod method, String path, ScRoutingEntry expected) {
    var routingContext = routingContext(method, path);
    when(pathProcessor.cleanIngressRequestPath(path)).thenReturn(path);
    var actual = requestMatchingService.lookupForIngressRequest(routingContext);
    assertThat(actual).isEqualTo(ofNullable(expected));
  }

  @Test
  void lookupForIngressRequest_positive_modulePrefixEnabled() {
    var path = "/mod-foo/foo/entities";
    var routingContext = routingContext(GET, path);
    when(pathProcessor.cleanIngressRequestPath(path)).thenReturn("/foo/entities");
    var actual = requestMatchingService.lookupForIngressRequest(routingContext);
    assertThat(actual).isPresent().contains(TestValues.scRoutingEntry("foo", "/foo/entities", GET, POST));
  }

  @Test
  void lookupForIngressRequest_positive_foundRouteWithModulePrefixEnabled() {
    var path = "/mod-bar/foo/entities/1";
    var routingContext = routingContext(GET, path);
    when(pathProcessor.cleanIngressRequestPath(path)).thenReturn(path);
    var actual = requestMatchingService.lookupForIngressRequest(routingContext);
    assertThat(actual).isPresent().contains(TestValues.scRoutingEntry("other-legacy", "/{id1}/*/{id2}", GET));
  }

  @Test
  void lookupForIngressRequest_positive_emptyModuleDescriptor() {
    requestMatchingService.ingressRequestCache.clear();

    var bootstrap = new ModuleBootstrap();
    bootstrap.setModule(new ModuleBootstrapDiscovery());
    requestMatchingService.bootstrapModule(bootstrap);

    var actual = requestMatchingService.lookupForIngressRequest(routingContext(GET, "/foo/entities"));
    assertThat(actual).isEmpty();
  }

  @Test
  void lookupForEgressRequest_positive() {
    var path = "/bar/entities";
    var routingContext = routingContext(POST, path);

    when(pathProcessor.cleanIngressRequestPath(path)).thenReturn(path);
    var actual = requestMatchingService.lookupForEgressRequest(routingContext);

    var expectedRoutingEntry = TestValues.routingEntry(path, array("POST"));
    expectedRoutingEntry.setPermissionsRequired(List.of("item.post"));
    var expectedScRoutingEntry = ScRoutingEntry.of("mod-bar-0.5.1", "http://mod-bar:8081", "bar", expectedRoutingEntry);

    assertThat(actual).isPresent().contains(expectedScRoutingEntry);
    assertThat(requestMatchingService.egressRequestCache).containsEntry(path, List.of(expectedScRoutingEntry));
  }

  @Test
  void lookupForEgressRequest_positive_cachedValue() {
    var path = "/bar/entities";
    var routingContext = routingContext(POST, path);
    var routingEntry = TestValues.routingEntry(path, array("POST"));
    var scRoutingEntry = ScRoutingEntry.of("mod-bar-0.5.1", "http://mod-bar:8081", "bar", routingEntry);
    when(pathProcessor.cleanIngressRequestPath(path)).thenReturn(path);
    requestMatchingService.egressRequestCache.put(path, List.of(scRoutingEntry));

    var actual = requestMatchingService.lookupForEgressRequest(routingContext);

    var rePath = "/bar/entities";
    var expectedRoutingEntry = TestValues.routingEntry(rePath, array("POST"));
    var expectedScRoutingEntry = ScRoutingEntry.of("mod-bar-0.5.1", "http://mod-bar:8081", "bar", expectedRoutingEntry);

    assertThat(actual).isPresent().contains(expectedScRoutingEntry);
  }

  @Test
  void lookupForEgressRequest_positive_multipleInterfaceSupports() {
    var path = "/bar/entities";
    var routingContext = routingContext(POST, path);
    var routingEntry = TestValues.routingEntry(path, array("POST", "PUT"));
    var scRoutingEntry1 = ScRoutingEntry.of("mod-bar-0.5.1", "http://mod-bar:8081", "bar", "multiple", routingEntry);
    var scRoutingEntry2 = ScRoutingEntry.of("mod-foo-1.1.1", "http://mod-foo:8081", "bar", "multiple", routingEntry);
    requestMatchingService.egressRequestCache.put(path, List.of(scRoutingEntry1, scRoutingEntry2));

    when(pathProcessor.cleanIngressRequestPath(path)).thenReturn(path);
    when(routingContext.request().getHeader(OkapiHeaders.MODULE_ID)).thenReturn("mod-foo-1.1.1");
    when(routingContext.request().getHeader(OkapiHeaders.REQUEST_ID)).thenReturn("reqId");

    var actual = requestMatchingService.lookupForEgressRequest(routingContext);

    var rePath = "/bar/entities";
    var expectedRoutingEntry = TestValues.routingEntry(rePath, array("POST", "PUT"));
    var expectedScRoutingEntry = ScRoutingEntry.of("mod-foo-1.1.1", "http://mod-foo:8081", "bar",
      "multiple", expectedRoutingEntry);

    assertThat(actual).isPresent().contains(expectedScRoutingEntry);
  }

  @Test
  void lookupForEgressRequest_negative_noModuleIdHeader() {
    var path = "/bar/entities";
    var routingContext = routingContext(POST, path);
    var routingEntry = TestValues.routingEntry(path, array("POST", "PUT"));
    var scRoutingEntry1 = ScRoutingEntry.of("mod-bar-0.5.1", "http://mod-bar:8081", "bar", "multiple", routingEntry);
    var scRoutingEntry2 = ScRoutingEntry.of("mod-foo-1.1.1", "http://mod-foo:8081", "bar", "multiple", routingEntry);
    requestMatchingService.egressRequestCache.put(path, List.of(scRoutingEntry1, scRoutingEntry2));

    when(pathProcessor.cleanIngressRequestPath(path)).thenReturn(path);
    when(routingContext.request().getHeader(OkapiHeaders.MODULE_ID)).thenReturn(null);
    when(routingContext.request().getHeader(OkapiHeaders.REQUEST_ID)).thenReturn("reqId");

    var actual = requestMatchingService.lookupForEgressRequest(routingContext);

    assertThat(actual).isEmpty();
  }

  @Test
  void lookupForEgressRequest_negative() {
    var routingContext = routingContext(POST, "/bar/entities/" + UUID.randomUUID());

    var actual = requestMatchingService.lookupForEgressRequest(routingContext);

    assertThat(actual).isEmpty();
  }

  @Test
  void lookupForEgressRequest_forwardUnknown_positive() {
    var routingContext = routingContext(POST, "/bar/entities/" + UUID.randomUUID());
    when(sidecarProperties.isForwardUnknownRequests()).thenReturn(true);

    var actual = requestMatchingService.lookupForEgressRequest(routingContext);

    assertThat(actual).isPresent().contains(TestValues.scGatewayEntry(TestConstants.GATEWAY_URL));
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

  private static RoutingContext routingContext(HttpMethod method, String path) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn(path);
    when(request.method()).thenReturn(method);
    return routingContext;
  }

  @SafeVarargs
  private static <T> T[] array(T... args) {
    return args;
  }
}
