package org.folio.sidecar.service.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeaderValidationFilterTest {

  private HeaderValidationFilter filter;

  @Mock
  private RoutingContext routingContext;
  @Mock
  private HttpServerRequest request;
  @Mock
  private HttpServerResponse response;

  @BeforeEach
  void setUp() {
    filter = new HeaderValidationFilter();
    when(routingContext.request()).thenReturn(request);
    when(routingContext.response()).thenReturn(response);
    when(response.setStatusCode(Response.Status.BAD_REQUEST.getStatusCode())).thenReturn(response);
    when(response.putHeader("Content-Type", "application/json")).thenReturn(response);
  }

  @Test
  void filter_shouldAllowRequestWithSingleOkapiHeader() {
    var headers = MultiMap.caseInsensitiveMultiMap()
      .add("x-okapi-tenant", "tenant1");
    when(request.headers()).thenReturn(headers);

    var result = filter.filter(routingContext);

    assertTrue(result.succeeded());
    assertEquals(routingContext, result.result());
  }

  @Test
  void filter_shouldAllowRequestWithMultipleDistinctOkapiHeaders() {
    var headers = MultiMap.caseInsensitiveMultiMap()
      .add("x-okapi-tenant", "tenant1")
      .add("x-okapi-token", "token1");
    when(request.headers()).thenReturn(headers);

    var result = filter.filter(routingContext);

    assertTrue(result.succeeded());
    assertEquals(routingContext, result.result());
  }

  @Test
  void filter_shouldRejectRequestWithDuplicateOkapiHeaders() {
    var headers = MultiMap.caseInsensitiveMultiMap()
      .add("x-okapi-tenant", "tenant1")
      .add("x-okapi-tenant", "tenant1");
    when(request.headers()).thenReturn(headers);

    var result = filter.filter(routingContext);

    assertFalse(result.succeeded());
    assertTrue(result.cause().getMessage().contains("x-okapi-tenant"));
  }

  @Test
  void filter_shouldAllowRequestWithDuplicateNonOkapiHeaders() {
    var headers = MultiMap.caseInsensitiveMultiMap()
      .add("content-type", "application/json")
      .add("content-type", "application/json")
      .add("x-okapi-tenant", "tenant1");
    when(request.headers()).thenReturn(headers);

    var result = filter.filter(routingContext);

    assertTrue(result.succeeded());
    assertEquals(routingContext, result.result());
  }

  @Test
  void filter_shouldRejectRequestWithMultipleDuplicateOkapiHeaders() {
    var headers = MultiMap.caseInsensitiveMultiMap()
      .add("x-okapi-tenant", "tenant1")
      .add("x-okapi-tenant", "tenant1")
      .add("x-okapi-token", "token1")
      .add("x-okapi-token", "token1");
    when(request.headers()).thenReturn(headers);

    var result = filter.filter(routingContext);

    assertFalse(result.succeeded());
    assertTrue(result.cause().getMessage().contains("x-okapi-tenant"));
    assertTrue(result.cause().getMessage().contains("x-okapi-token"));
  }
}