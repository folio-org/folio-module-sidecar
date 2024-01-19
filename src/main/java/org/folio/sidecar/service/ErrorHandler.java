package org.folio.sidecar.service;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.FORBIDDEN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.INTERNAL_SERVER_ERROR;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.UNAUTHORIZED;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.exception.TenantNotEnabledException;
import org.folio.sidecar.model.error.Error;
import org.folio.sidecar.model.error.ErrorCode;
import org.folio.sidecar.model.error.ErrorResponse;
import org.folio.sidecar.model.error.Parameter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class ErrorHandler {

  private final JsonConverter jsonConverter;
  private final SidecarSignatureService sidecarSignatureService;

  /**
   * Sends error response for the given {@link RoutingContext} and {@link Throwable} error as internal server error.
   *
   * @param rc - routing context as {@link RoutingContext} object
   * @param throwable - error as {@link Throwable} object
   */
  public void sendErrorResponse(RoutingContext rc, Throwable throwable) {
    if (throwable instanceof ForbiddenException) {
      sendErrorResponse(rc, throwable, FORBIDDEN, ErrorCode.AUTHORIZATION_ERROR, "Access Denied");
      return;
    }

    if (throwable instanceof UnauthorizedException) {
      sendErrorResponse(rc, throwable, UNAUTHORIZED, ErrorCode.AUTHORIZATION_ERROR, "Unauthorized");
      return;
    }

    if (throwable instanceof WebApplicationException webApplicationException) {
      var errorCode = throwable instanceof NotFoundException ? ErrorCode.ROUTE_FOUND_ERROR : ErrorCode.SERVICE_ERROR;
      var statusCode = webApplicationException.getResponse().getStatus();
      sendErrorResponse(rc, throwable, statusCode, errorCode, null);
      return;
    }

    if (throwable instanceof TenantNotEnabledException) {
      sendErrorResponse(rc, throwable, BAD_REQUEST, ErrorCode.UNKNOWN_TENANT, null);
      return;
    }

    sendErrorResponse(rc, throwable, INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_ERROR, null);
  }

  private void sendErrorResponse(RoutingContext rc, Throwable error, int status, ErrorCode code, String msgOverride) {
    sidecarSignatureService.removeSignature(rc);
    log.debug("Handling error from request processing", error);
    log.warn("Sending error response: type = {}, message = {}", error.getClass().getSimpleName(), error.getMessage());
    var response = rc.response();
    if (!response.ended()) {
      response
        .setStatusCode(status)
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(jsonConverter.toJson(buildResponseEntity(code, error, msgOverride)));
    }
  }

  private static ErrorResponse buildResponseEntity(ErrorCode code, Throwable throwable, String messageOverride) {
    var errorParameters = getErrorParameters(throwable);
    return new ErrorResponse()
      .totalRecords(1)
      .errors(List.of(new Error()
        .message(messageOverride != null ? messageOverride : throwable.getMessage())
        .code(code)
        .type(throwable.getClass().getSimpleName())
        .parameters(errorParameters.isEmpty() ? null : errorParameters)));
  }

  private static List<Parameter> getErrorParameters(Throwable err) {
    var cause = err.getCause();
    var causeMessage = cause != null ? cause.getMessage() : null;
    if (causeMessage == null || err instanceof ForbiddenException || err instanceof UnauthorizedException) {
      return Collections.emptyList();
    }

    return List.of(new Parameter().key("cause").value(causeMessage));
  }
}
