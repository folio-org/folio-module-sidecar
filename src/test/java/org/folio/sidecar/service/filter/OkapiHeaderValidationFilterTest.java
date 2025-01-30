package org.folio.sidecar.service.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.folio.sidecar.exception.DuplicateHeaderException;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiHeaderValidationFilterTest {

  @Mock private OkapiHeaderValidationService validationService;
  @Mock private RoutingContext routingContext;
  @Mock private HttpServerRequest request;

  private OkapiHeaderValidationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new OkapiHeaderValidationFilter(validationService);
    when(routingContext.request()).thenReturn(request);
  }

  @Test
  void filter_positive() {
    var result = filter.filter(routingContext);

    verify(validationService).validateHeaders(request);
    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void filter_negative_duplicateHeaders() {
    var duplicateHeaders = List.of("X-Okapi-Tenant", "X-Okapi-Token");
    var exception = new DuplicateHeaderException("Duplicate headers found", duplicateHeaders);
    doThrow(exception).when(validationService).validateHeaders(request);

    var result = filter.filter(routingContext);

    verify(validationService).validateHeaders(request);
    assertThat(result.failed()).isFalse();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void getOrder_positive() {
    assertThat(filter.getOrder()).isEqualTo(IngressFilterOrder.HEADER_VALIDATION.getOrder());
  }
}