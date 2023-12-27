package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @CsvSource({
    "/users,false,/users",
    "/mod-foo/users,false,/mod-foo/users",
    "/mod-foo/users,true,/users",
    "/mod-bar/users,true,/mod-bar/users",
  })
  @DisplayName("updatePath_parameterized")
  @ParameterizedTest
  void updatePath_parameterized(String givenPath, boolean modulePrefixEnabled, String expectedPath) {
    var routingContext = mock(RoutingContext.class);
    var httpRequest = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(httpRequest);
    when(httpRequest.path()).thenReturn(givenPath);

    var actual = RoutingUtils.updatePath(routingContext, modulePrefixEnabled, TestConstants.MODULE_NAME);

    assertThat(actual).isEqualTo(expectedPath);
  }

  @CsvSource({
    "/users, false, /users",
    "/mod-foo/users, false, /mod-foo/users",
    "/mod-foo/users, true, /mod-foo/users",
    "/users, true, /mod-foo/users",
    "/mod-bar/users, true, /mod-foo/mod-bar/users",
  })
  @DisplayName("updatePath_parameterized")
  @ParameterizedTest
  void buildPathWithPrefix_parameterized(String sourcePath, boolean modulePrefixEnabled, String expectedPath) {
    var routingContext = mock(RoutingContext.class);
    var httpRequest = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(httpRequest);
    when(httpRequest.path()).thenReturn(sourcePath);

    var actual = RoutingUtils.buildPathWithPrefix(routingContext, modulePrefixEnabled, TestConstants.MODULE_NAME);

    assertThat(actual).isEqualTo(expectedPath);
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
