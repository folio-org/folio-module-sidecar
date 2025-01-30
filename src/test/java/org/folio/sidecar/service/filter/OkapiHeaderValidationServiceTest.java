package org.folio.sidecar.service.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.folio.sidecar.exception.DuplicateHeaderException;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class OkapiHeaderValidationServiceTest {

  private OkapiHeaderValidationService validationService;
  private HttpServerRequest request;
  private HeadersMultiMap headers;

  @BeforeEach
  void setUp() {
    validationService = new OkapiHeaderValidationService();
    request = mock(HttpServerRequest.class);
    headers = new HeadersMultiMap();
    when(request.headers()).thenReturn(headers);
  }

  @Test
  void validateHeaders_positive() {
    headers.set(OkapiHeaders.TENANT, "tenant1");
    headers.set(OkapiHeaders.TOKEN, "token1");
    headers.set(OkapiHeaders.USER_ID, "user1");

    validationService.validateHeaders(request);
  }

  @Test
  void validateHeaders_negative_duplicateHeaders() {
    headers.add(OkapiHeaders.TENANT, "tenant1");
    headers.add(OkapiHeaders.TENANT, "tenant2");
    headers.add(OkapiHeaders.TOKEN, "token1");
    headers.add(OkapiHeaders.TOKEN, "token2");

    var exception = assertThrows(DuplicateHeaderException.class,
      () -> validationService.validateHeaders(request));

    assertThat(exception.getDuplicateHeaders()).containsExactlyInAnyOrder(
      OkapiHeaders.TENANT,
      OkapiHeaders.TOKEN
    );
  }

  @Test
  void validateHeaders_positive_differentCases() {
    headers.set(OkapiHeaders.TENANT.toLowerCase(), "tenant1");
    headers.set(OkapiHeaders.TOKEN.toUpperCase(), "token1");

    validationService.validateHeaders(request);
  }

  @Test
  void validateHeaders_negative_duplicateHeadersDifferentCases() {
    headers.add(OkapiHeaders.TENANT, "tenant1");
    headers.add(OkapiHeaders.TENANT.toLowerCase(), "tenant2");

    var exception = assertThrows(DuplicateHeaderException.class,
      () -> validationService.validateHeaders(request));

    assertThat(exception.getDuplicateHeaders()).containsExactly(OkapiHeaders.TENANT.toLowerCase());
  }
}