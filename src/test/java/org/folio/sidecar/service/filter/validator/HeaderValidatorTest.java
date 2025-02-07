package org.folio.sidecar.service.filter.validator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HeaderValidatorTest {

  private HeaderValidator headerValidator;
  private MultiMap headers;

  @Mock
  private RoutingContext routingContext;
  @Mock
  private HttpServerRequest request;

  @BeforeEach
  void setUp() {
    headerValidator = new HeaderValidator();
    headers = MultiMap.caseInsensitiveMultiMap();
    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);
  }

  @Test
  void validate_positive_blankTenant() {
    headers.set(OkapiHeaders.TENANT, "");
    headerValidator.validateOkapiHeaders(routingContext);
  }

  @Test
  void validate_positive_noTenant() {
    headerValidator.validateOkapiHeaders(routingContext);
  }

  @Test
  void validate_positive_duplicatedTenant() {
    headers.add(OkapiHeaders.TENANT, "tenant1");
    headers.add(OkapiHeaders.TENANT, "tenant2");
    
    assertThrows(IllegalArgumentException.class, () -> headerValidator.validateOkapiHeaders(routingContext));
  }

  @Test
  void validate_positive_validTenantAndToken() {
    headers.set(OkapiHeaders.TENANT, "tenant1");
    headers.set(OkapiHeaders.TOKEN, "token1");
    
    headerValidator.validateOkapiHeaders(routingContext);
  }

  @Test
  void validate_positive_duplicatedToken() {
    headers.add(OkapiHeaders.TOKEN, "token1");
    headers.add(OkapiHeaders.TOKEN, "token2");
    
    assertThrows(IllegalArgumentException.class, () -> headerValidator.validateOkapiHeaders(routingContext));
  }
}