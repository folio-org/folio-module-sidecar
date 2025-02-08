package org.folio.sidecar.service.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.exception.DuplicateHeaderException;
import org.folio.sidecar.service.header.OkapiHeaderValidator;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiHeaderValidationFilterTest {

  @Mock
  private OkapiHeaderValidator headerValidator;

  @Mock
  private RoutingContext routingContext;

  @Mock
  private HttpServerRequest request;

  private MultiMap headers;

  @InjectMocks
  private OkapiHeaderValidationFilter filter;

  @BeforeEach
  void setUp() {
    // Initialize headers with a case-insensitive MultiMap implementation.
    headers = MultiMap.caseInsensitiveMultiMap();

    lenient().when(routingContext.request()).thenReturn(request);
    lenient().when(request.headers()).thenReturn(headers);
  }

  @Test
  void shouldReturnSucceededFutureWhenHeadersAreValid() {
    // Given: headerValidator.validateOkapiHeaders does not throw an exception (default behavior)
    // Optionally, add a valid Okapi header to headers if desired.
    headers.add("x-okapi-token", "someToken");

    // When: calling filter() with the routingContext
    Future<RoutingContext> future = filter.filter(routingContext);

    // Then: the future should succeed and return the same RoutingContext
    assertTrue(future.succeeded(), "Future should succeed when headers are valid");
    assertEquals(routingContext, future.result(), "The returned RoutingContext should be the same as input");
    verify(headerValidator).validateOkapiHeaders(headers);
  }

  @Test
  void shouldReturnFailedFutureWhenHeadersAreInvalid() {
    // Given: headerValidator.validateOkapiHeaders throws a DuplicateHeaderException
    DuplicateHeaderException exception = new DuplicateHeaderException("duplicate header");
    doThrow(exception).when(headerValidator).validateOkapiHeaders(headers);

    // When: calling filter() with the routingContext
    Future<RoutingContext> future = filter.filter(routingContext);

    // Then: the future should be failed with the thrown exception
    assertTrue(future.failed(), "Future should fail when header validation throws an exception");
    assertEquals(exception, future.cause(), "The cause of failure should be the DuplicateHeaderException");
    verify(headerValidator).validateOkapiHeaders(headers);
  }
}
