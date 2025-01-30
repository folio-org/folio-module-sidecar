package org.folio.sidecar.service.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.model.error.ErrorCode;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class HeaderValidationFilterTest {

  private HeaderValidationFilter filter;
  private RoutingContext routingContext;
  private HttpServerRequest request;
  private HeadersMultiMap headers;

  @BeforeEach
  void setUp() {
    filter = new HeaderValidationFilter();
    routingContext = mock(RoutingContext.class);
    request = mock(HttpServerRequest.class);
    headers = new HeadersMultiMap();

    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "x-okapi-tenant",
    "X-Okapi-Token",
    "X-OKAPI-USER-ID"
  })
  void filter_positive_singleOkapiHeader(String headerName) {
    headers.add(headerName, "value1");

    var result = filter.filter(routingContext);

    assertThat(result).isCompleted()
      .matches(future -> future.result() == routingContext);
  }

  @Test
  void filter_positive_multipleDistinctOkapiHeaders() {
    headers.add("x-okapi-tenant", "tenant1");
    headers.add("x-okapi-token", "token1");
    headers.add("x-okapi-user-id", "user1");

    var result = filter.filter(routingContext);

    assertThat(result).isCompleted()
      .matches(future -> future.result() == routingContext);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "x-okapi-tenant",
    "X-Okapi-Token",
    "X-OKAPI-USER-ID"
  })
  void filter_negative_duplicateOkapiHeaders(String headerName) {
    headers.add(headerName, "value1");
    headers.add(headerName, "value2");

    var result = filter.filter(routingContext);

    assertThat(result).isCompletedExceptionally()
      .satisfies(future -> {
        var exception = (ValidationException) result.cause();
        var error = exception.getError();
        assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error.getMessage()).contains(headerName);
      });
  }

  @Test
  void filter_positive_duplicateNonOkapiHeaders() {
    headers.add("Content-Type", "application/json");
    headers.add("Content-Type", "charset=utf-8");

    var result = filter.filter(routingContext);

    assertThat(result).isCompleted()
      .matches(future -> future.result() == routingContext);
  }

  @Test
  void filter_negative_multipleDuplicateOkapiHeaders() {
    headers.add("x-okapi-tenant", "tenant1");
    headers.add("x-okapi-tenant", "tenant2");
    headers.add("x-okapi-token", "token1");
    headers.add("x-okapi-token", "token2");

    var result = filter.filter(routingContext);

    assertThat(result).isCompletedExceptionally()
      .satisfies(future -> {
        var exception = (ValidationException) result.cause();
        var error = exception.getError();
        assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error.getMessage())
          .contains("x-okapi-tenant")
          .contains("x-okapi-token");
      });
  }
}