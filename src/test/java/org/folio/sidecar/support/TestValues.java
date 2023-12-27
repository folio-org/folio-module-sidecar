package org.folio.sidecar.support;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import io.quarkus.vertx.http.runtime.QuarkusHttpHeaders;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.Arrays;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;

public class TestValues {

  public static ScRoutingEntry scRoutingEntry(String interfaceId, String pathPattern, HttpMethod... httpMethods) {
    var methods = Arrays.stream(httpMethods).map(HttpMethod::name).toArray(String[]::new);
    return ScRoutingEntry.of(
      TestConstants.MODULE_ID, TestConstants.MODULE_URL, interfaceId, routingEntry(pathPattern, methods));
  }

  @SuppressWarnings("SameParameterValue")
  public static ScRoutingEntry scRoutingEntry(String interfaceId, String pathPattern, String... httpMethods) {
    return ScRoutingEntry.of(
      TestConstants.MODULE_ID, TestConstants.MODULE_URL, interfaceId, routingEntry(pathPattern, httpMethods));
  }

  public static ScRoutingEntry scRoutingEntry(String interfaceId, ModuleBootstrapEndpoint routingEntry) {
    return ScRoutingEntry.of(TestConstants.MODULE_ID, TestConstants.MODULE_URL, interfaceId, routingEntry);
  }

  public static ScRoutingEntry scRoutingEntrySysInterface(String interfaceId, String pathPattern,
    HttpMethod... httpMethods) {
    var methods = Arrays.stream(httpMethods).map(HttpMethod::name).toArray(String[]::new);
    return ScRoutingEntry.of(
      TestConstants.MODULE_ID, TestConstants.MODULE_URL, interfaceId, "system", routingEntry(pathPattern, methods));
  }

  public static ModuleBootstrapEndpoint routingEntry(String pathPattern, String[] methods) {
    var routingEntry = new ModuleBootstrapEndpoint();
    routingEntry.setPathPattern(pathPattern);
    routingEntry.setMethods(methods);
    return routingEntry;
  }

  @SuppressWarnings("SameParameterValue")
  public static ModuleBootstrapEndpoint routingEntryWithPath(String path, HttpMethod... httpMethods) {
    var methods = Arrays.stream(httpMethods).map(HttpMethod::name).toArray(String[]::new);
    var routingEntry = new ModuleBootstrapEndpoint();
    routingEntry.setPath(path);
    routingEntry.setMethods(methods);
    return routingEntry;
  }

  public static ScRoutingEntry scGatewayEntry(String location) {
    return ScRoutingEntry.of("NONE", location, "GATEWAY", null);
  }

  public static RoutingContext routingContext(String tenant, ScRoutingEntry scRoutingEntry) {
    var headers = new QuarkusHttpHeaders().add(OkapiHeaders.TENANT, tenant);
    return routingContext(scRoutingEntry, headers);
  }

  public static RoutingContext routingContext(ScRoutingEntry scRoutingEntry, MultiMap headers) {
    var request = mock(HttpServerRequest.class);
    return routingContext(scRoutingEntry, headers, request);
  }

  public static RoutingContext routingContext(ScRoutingEntry scRoutingEntry, MultiMap headers,
    HttpServerRequest request) {
    var routingContext = mock(RoutingContext.class);
    lenient().when(routingContext.<ScRoutingEntry>get("scRoutingEntry")).thenReturn(scRoutingEntry);
    lenient().when(routingContext.request()).thenReturn(request);
    lenient().when(request.headers()).thenReturn(headers);
    return routingContext;
  }

  public static RoutingContext routingContext(MultiMap headers, HttpServerRequest request) {
    var routingContext = mock(RoutingContext.class);
    lenient().when(routingContext.request()).thenReturn(request);
    lenient().when(request.headers()).thenReturn(headers);
    return routingContext;
  }

  public static RoutingContext routingContext(MultiMap headers) {
    var scRoutingEntry = TestValues.scRoutingEntry("foo", "/foo", HttpMethod.POST);
    return routingContext(scRoutingEntry, headers);
  }

  public static RoutingContext routingContext(String tenant) {
    var scRoutingEntry = TestValues.scRoutingEntry("foo", "/foo", HttpMethod.POST);
    return routingContext(tenant, scRoutingEntry);
  }
}
