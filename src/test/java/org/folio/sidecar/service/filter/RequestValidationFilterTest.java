package org.folio.sidecar.service.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.service.filter.IngressFilterOrder.REQUEST_VALIDATION;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.BadRequestException;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RequestValidationFilterTest {

  @Mock private RoutingContext routingContext;
  @Mock private HttpServerRequest request;
  @Mock private MultiMap headers;

  private final RequestValidationFilter requestValidationFilter = new RequestValidationFilter();

  @Test
  void filter_positive_noBodyForGetRequestWhenContentLengthDoesNotExist() {
    when(routingContext.request()).thenReturn(request);
    when(routingContext.request().method()).thenReturn(HttpMethod.GET);
    when(request.headers()).thenReturn(headers);
    when(headers.contains("Content-Length")).thenReturn(false);

    var result = requestValidationFilter.filter(routingContext);

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  void filter_positive_noBodyForGetRequestWhenContentLengthIsZero() {
    when(routingContext.request()).thenReturn(request);
    when(routingContext.request().method()).thenReturn(HttpMethod.GET);
    when(request.headers()).thenReturn(headers);
    when(headers.contains("Content-Length")).thenReturn(true);
    when(headers.get("Content-Length")).thenReturn("0");

    var result = requestValidationFilter.filter(routingContext);

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  void filter_negative_bodyPresentForGetRequest() {
    when(routingContext.request()).thenReturn(request);
    when(routingContext.request().method()).thenReturn(HttpMethod.GET);
    when(request.headers()).thenReturn(headers);
    when(headers.contains("Content-Length")).thenReturn(true);
    when(headers.get("Content-Length")).thenReturn("1");

    var result = requestValidationFilter.filter(routingContext);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause())
      .isInstanceOf(BadRequestException.class)
      .hasMessage("GET requests should not have a body");
  }

  @Test
  void getOrder_positive() {
    var order = requestValidationFilter.getOrder();

    assertThat(order).isEqualTo(REQUEST_VALIDATION.getOrder());
  }
}
