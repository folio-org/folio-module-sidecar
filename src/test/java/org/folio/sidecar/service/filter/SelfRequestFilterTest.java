package org.folio.sidecar.service.filter;

import static io.vertx.core.http.HttpMethod.GET;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.sidecar.service.filter.IngressFilterOrder.SELF_REQUEST;
import static org.folio.sidecar.utils.RoutingUtils.SELF_REQUEST_KEY;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SelfRequestFilterTest {

  @InjectMocks private SelfRequestFilter selfRequestFilter;
  @Mock private SidecarSignatureService selfRequestService;

  @Test
  void filter_positive() {
    var ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(selfRequestService.isSelfRequest(ctx)).thenReturn(true);
    when(ctx.request().method()).thenReturn(GET);
    when(ctx.request().uri()).thenReturn("/path");

    selfRequestFilter.filter(ctx);

    verify(selfRequestService).isSelfRequest(ctx);
    verify(ctx).put(SELF_REQUEST_KEY, true);
  }

  @Test
  void getOrder_positive() {
    var order = selfRequestFilter.getOrder();

    assertThat(order).isEqualTo(SELF_REQUEST.getOrder());
  }
}
