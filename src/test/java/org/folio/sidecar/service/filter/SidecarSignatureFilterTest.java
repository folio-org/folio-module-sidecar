package org.folio.sidecar.service.filter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.sidecar.service.filter.IngressFilterOrder.SIDECAR_SIGNATURE;
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
class SidecarSignatureFilterTest {

  @InjectMocks private SidecarSignatureFilter sidecarSignatureFilter;
  @Mock private SidecarSignatureService sidecarSignatureService;

  @Test
  void filter_positive() {
    var ctx = mock(RoutingContext.class);
    when(sidecarSignatureService.populateSignature(ctx)).thenReturn(ctx);

    sidecarSignatureFilter.filter(ctx);

    verify(sidecarSignatureService).populateSignature(ctx);
  }

  @Test
  void getOrder_positive() {
    var order = sidecarSignatureFilter.getOrder();

    assertThat(order).isEqualTo(SIDECAR_SIGNATURE.getOrder());
  }
}
