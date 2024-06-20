package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RoutingUtilsTest {

  @Test
  void getRequestId_positive_newRequest() {
    var routingContext = routingContext(null);
    var actual = RoutingUtils.getRequestId(routingContext);
    assertThat(actual).isNotNull().matches("\\d{6}/foo");
  }

  @Test
  void getRequestId_positive_nextRequest() {
    var routingContext = routingContext("111111/users");
    var actual = RoutingUtils.getRequestId(routingContext);
    assertThat(actual).isNotNull().matches("111111/users;\\d{6}/foo");
  }

  private static RoutingContext routingContext(String requestId) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(request);
    when(request.method()).thenReturn(HttpMethod.GET);
    when(request.path()).thenReturn("/foo/entities");
    when(request.getHeader(OkapiHeaders.REQUEST_ID)).thenReturn(requestId);
    return routingContext;
  }
}
