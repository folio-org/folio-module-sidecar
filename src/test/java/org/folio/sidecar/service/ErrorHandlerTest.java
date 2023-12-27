package org.folio.sidecar.service;

import static io.vertx.core.Future.succeededFuture;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.security.UnauthorizedException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.UUID;
import org.apache.http.ParseException;
import org.folio.sidecar.exception.TenantNotEnabledException;
import org.folio.sidecar.model.error.ErrorResponse;
import org.folio.sidecar.support.TestUtils;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ErrorHandlerTest {

  @Spy private final JsonConverter jsonConverter = new JsonConverter(TestUtils.OBJECT_MAPPER);

  @InjectMocks private ErrorHandler errorHandler;
  @Mock private SidecarSignatureService sidecarSignatureService;
  @Captor private ArgumentCaptor<String> responseCaptor;
  @Captor private ArgumentCaptor<Integer> responseStatusCaptor;

  @Test
  void sendErrorResponse_positive_throwable() {
    var routingContext = routingContext();

    errorHandler.sendErrorResponse(routingContext, new RuntimeException("error"));

    assertThat(responseCaptor.getValue()).isEqualTo(TestUtils.minify(TestUtils.readString("json/runtime-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_forbiddenException() {
    var routingContext = routingContext();

    errorHandler.sendErrorResponse(routingContext, new ForbiddenException("Access denied to tenant: test-tenant"));

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/forbidden-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_FORBIDDEN);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_forbiddenExceptionWithCause() {
    var routingContext = routingContext();
    var userId = UUID.randomUUID();

    var cause = new NotFoundException("Failed to find user by id: " + userId);
    var throwable = new ForbiddenException("Access denied to tenant: test-tenant", cause);
    errorHandler.sendErrorResponse(routingContext, throwable);

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/forbidden-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_FORBIDDEN);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_unauthorizedError() {
    var routingContext = routingContext();

    errorHandler.sendErrorResponse(routingContext, new UnauthorizedException("Token is expired"));

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/unauthorized-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_UNAUTHORIZED);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_unauthorizedErrorWithCause() {
    var routingContext = routingContext();

    var cause = new ParseException("SRJWT07000: Failed to verify a token");
    errorHandler.sendErrorResponse(routingContext, new UnauthorizedException("Token is expired", cause));

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/unauthorized-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_UNAUTHORIZED);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_throwableWithCause() {
    var routingContext = routingContext();
    var cause = new IllegalArgumentException("Invalid argument");

    errorHandler.sendErrorResponse(routingContext, new RuntimeException("error", cause));

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/runtime-error-with-cause.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_webApplicationException() {
    var routingContext = routingContext();
    var errorMessage = "Module location is not found for moduleId: mod-foo-0.2.1";

    errorHandler.sendErrorResponse(routingContext, new BadRequestException(errorMessage));

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/module-url-not-found-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_BAD_REQUEST);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_notFoundError() {
    var routingContext = routingContext();
    var errorMessage = "Route is not found: /foo/entities";

    errorHandler.sendErrorResponse(routingContext, new NotFoundException(errorMessage));

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/route-not-found-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_NOT_FOUND);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_tenantNotEnabledError() {
    var routingContext = routingContext();

    errorHandler.sendErrorResponse(routingContext, new TenantNotEnabledException("test-tenant"));

    assertThat(responseCaptor.getValue()).isEqualTo(
      TestUtils.minify(TestUtils.readString("json/tenant-not-enabled-error.json")));
    assertThat(responseStatusCaptor.getValue()).isEqualTo(SC_BAD_REQUEST);
    verify(jsonConverter).toJson(any(ErrorResponse.class));
    verify(sidecarSignatureService).removeSignature(routingContext);
  }

  @Test
  void sendErrorResponse_positive_responseIsEnded() {
    var routingContext = mock(RoutingContext.class);
    var response = mock(HttpServerResponse.class);
    when(routingContext.response()).thenReturn(response);
    when(response.ended()).thenReturn(true);

    errorHandler.sendErrorResponse(routingContext, new TenantNotEnabledException("test-tenant"));
    verify(routingContext.response(), never()).end(anyString());
  }

  private RoutingContext routingContext() {
    var routingContext = mock(RoutingContext.class);
    var response = mock(HttpServerResponse.class);
    when(response.ended()).thenReturn(false);
    when(routingContext.response()).thenReturn(response);
    when(response.setStatusCode(responseStatusCaptor.capture())).thenReturn(response);
    when(response.putHeader(CONTENT_TYPE, APPLICATION_JSON)).thenReturn(response);
    when(response.end(responseCaptor.capture())).thenReturn(succeededFuture());
    return routingContext;
  }
}
