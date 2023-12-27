package org.folio.sidecar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.TestValues;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SidecarSignatureServiceTest {

  private static final String SIGNATURE_HEADER = "x-okapi-sidecar-signature";

  private final SidecarSignatureService sidecarSignatureService = new SidecarSignatureService();

  @Test
  void removeSignature_positive_context() {
    var headers = Mockito.mock(MultiMap.class);
    var routingContext = TestValues.routingContext(headers);

    sidecarSignatureService.removeSignature(routingContext);

    verify(headers).remove(SIGNATURE_HEADER);
  }

  @Test
  void removeSignature_positive_response() {
    var request = Mockito.mock(HttpServerRequest.class);
    var headers = Mockito.mock(MultiMap.class);

    var routingContext = TestValues.routingContext(headers, request);

    sidecarSignatureService.removeSignature(routingContext);

    verify(headers).remove(SIGNATURE_HEADER);
  }

  @Test
  void populateSignature_positive() {
    var headers = Mockito.mock(MultiMap.class);
    var routingContext = TestValues.routingContext(headers);

    sidecarSignatureService.populateSignature(routingContext);

    verify(headers).add(eq(SIGNATURE_HEADER), any(String.class));
  }

  @Test
  void isSelfRequest_positive() {
    var routingContext = Mockito.mock(RoutingContext.class);
    var request = Mockito.mock(HttpServerRequest.class);
    var signature = TestUtils.getSignature();

    when(routingContext.request()).thenReturn(request);
    when(request.getHeader(SIGNATURE_HEADER)).thenReturn(signature);

    assertThat(sidecarSignatureService.isSelfRequest(routingContext)).isTrue();
  }
}
