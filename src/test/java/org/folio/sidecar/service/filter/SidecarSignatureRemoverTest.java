package org.folio.sidecar.service.filter;

import static org.mockito.Mockito.verify;

import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestValues;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SidecarSignatureRemoverTest {

  private static final String SIGNATURE = "signature";
  private static final String SIGNATURE_HEADER = "x-sidecar-signature";

  @InjectMocks private SidecarSignatureRemover sidecarSignatureRemover;
  @Mock private SidecarSignatureService sidecarSignatureService;

  @Test
  void filter_positive_removeSignature() {
    var routingContext = TestValues.routingContext(TestConstants.TENANT_NAME);
    routingContext.request().headers().add(SIGNATURE_HEADER, SIGNATURE);

    sidecarSignatureRemover.filter(routingContext);

    verify(sidecarSignatureService).removeSignature(routingContext);
  }
}
