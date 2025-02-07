package org.folio.sidecar.service.filter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OkapiHeaderValidationFilterTest {

  private OkapiHeaderValidationFilter filter;
  private RoutingContext routingContext;
  private HttpServerRequest request;
  private MultiMap headers;

  @BeforeEach
  void setUp() {
    filter = new OkapiHeaderValidationFilter();
    routingContext = mock(RoutingContext.class);
    request = mock(HttpServerRequest.class);
    headers = MultiMap.caseInsensitiveMultiMap();

    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);
  }

  @Test
  void shouldAcceptRequestWithUniqueHeaders() {
    headers.add(OkapiHeaders.TENANT, "tenant1");
    headers.add(OkapiHeaders.TOKEN, "token1");

    var result = filter.filter(routingContext);

    assertTrue(result.succeeded());
  }

  @Test
  void shouldRejectRequestWithDuplicateHeadersSameValue() {
    headers.add(OkapiHeaders.TENANT, "tenant1");
    headers.add(OkapiHeaders.TENANT, "tenant1");

    var result = filter.filter(routingContext);

    assertTrue(result.failed());
    assertTrue(result.cause().getMessage().contains("duplicate Okapi headers"));
  }

  @Test
  void shouldRejectRequestWithDuplicateHeadersDifferentValues() {
    headers.add(OkapiHeaders.TENANT, "tenant1");
    headers.add(OkapiHeaders.TENANT, "tenant2");

    var result = filter.filter(routingContext);

    assertTrue(result.failed());
    assertTrue(result.cause().getMessage().contains("duplicate Okapi headers"));
  }

  @Test
  void shouldHandleHeadersCaseInsensitively() {
    headers.add(OkapiHeaders.TENANT.toLowerCase(), "tenant1");
    headers.add(OkapiHeaders.TENANT.toUpperCase(), "tenant1");

    var result = filter.filter(routingContext);

    assertTrue(result.failed());
    assertTrue(result.cause().getMessage().contains("duplicate Okapi headers"));
  }

  @Test
  void shouldAllowNonOkapiDuplicateHeaders() {
    headers.add("Content-Type", "application/json");
    headers.add("Content-Type", "charset=utf-8");
    headers.add(OkapiHeaders.TENANT, "tenant1");

    var result = filter.filter(routingContext);

    assertTrue(result.succeeded());
  }
}