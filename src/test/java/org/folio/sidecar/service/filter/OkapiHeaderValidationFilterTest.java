package org.folio.sidecar.service.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.exception.DuplicateHeaderException;
import org.folio.sidecar.service.header.OkapiHeaderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OkapiHeaderValidationFilterTest {

  @Mock
  private OkapiHeaderValidator headerValidator;

  @Mock
  private RoutingContext routingContext;

  @Mock
  private HttpServerRequest request;

  @Mock
  private MultiMap headers;

  @InjectMocks
  private OkapiHeaderValidationFilter filter;

  @BeforeEach
  void setUp() {
    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);
  }

  @Test
  void filter_positive() {
    filter.filter(routingContext);

    verify(headerValidator).validateOkapiHeaders(headers);
  }

  @Test
  void filter_withDuplicateHeaders_negative() {
    doThrow(new DuplicateHeaderException("x-okapi-tenant"))
      .when(headerValidator).validateOkapiHeaders(any());

    filter.filter(routingContext);

    verify(headerValidator).validateOkapiHeaders(headers);
  }

  @Test
  void getOrder_positive() {
    assert filter.getOrder() == IngressFilterOrder.HEADER_VALIDATION.getOrder();
  }
}