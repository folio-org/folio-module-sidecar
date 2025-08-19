package org.folio.sidecar.service;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.FORBIDDEN;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.INTERNAL_SERVER_ERROR;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.REQUEST_TIMEOUT;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.UNAUTHORIZED;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.exception.TenantNotEnabledException;
import org.folio.sidecar.model.error.Error;
import org.folio.sidecar.model.error.ErrorCode;
import org.folio.sidecar.model.error.ErrorResponse;
import org.folio.sidecar.model.error.Parameter;

@Log4j2
@ApplicationScoped
public class ErrorHandler {

  private final JsonConverter jsonConverter;
  private final SidecarSignatureService sidecarSignatureService;
  private final ErrHandler errHandler;

  public ErrorHandler(JsonConverter jsonConverter, SidecarSignatureService sidecarSignatureService) {
    this.jsonConverter = jsonConverter;
    this.sidecarSignatureService = sidecarSignatureService;

    this.errHandler = createHandler();
  }

  /**
   * Sends error response for the given {@link RoutingContext} and {@link Throwable} error as internal server error.
   *
   * @param rc - routing context as {@link RoutingContext} object
   * @param throwable - error as {@link Throwable} object
   */
  public void sendErrorResponse(RoutingContext rc, Throwable throwable) {
    log.debug("Handling error from request processing", throwable);

    var cause = (throwable instanceof CompletionException) ? getRootCause(throwable) : throwable;

    errHandler.handle(cause, rc);
  }

  private void sendErrorResponse(RoutingContext rc, Throwable error, int status, ErrorCode code, String msgOverride) {
    sidecarSignatureService.removeSignature(rc);

    log.warn("Sending error response for [method: {}, uri: {}]: type = {}, message = {}",
      () -> rc.request().method(), dumpUri(rc), () -> error.getClass().getSimpleName(), error::getMessage);

    var response = rc.response();
    if (!response.ended()) {
      response
        .setStatusCode(status)
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(jsonConverter.toJson(buildResponseEntity(code, error, msgOverride)));
    }
  }

  private ErrHandler createHandler() {
    return ErrHandler.builder()
      .add(
        ForbiddenException.class, (cause, rc) ->
          sendErrorResponse(rc, cause, FORBIDDEN, ErrorCode.AUTHORIZATION_ERROR, "Access Denied"))
      .add(
        UnauthorizedException.class, (cause, rc) ->
          sendErrorResponse(rc, cause, UNAUTHORIZED, ErrorCode.AUTHORIZATION_ERROR, "Unauthorized"))
      .add(
        cause -> cause.getCause() instanceof TimeoutException, (cause, rc) ->
          sendErrorResponse(rc, cause.getCause(), REQUEST_TIMEOUT, ErrorCode.READ_TIMEOUT_ERROR, "Request Timeout"))
      .add(
        WebApplicationException.class, (cause, rc) -> {
          var errorCode = cause instanceof NotFoundException ? ErrorCode.ROUTE_FOUND_ERROR : ErrorCode.SERVICE_ERROR;
          var statusCode = ((WebApplicationException) cause).getResponse().getStatus();
          sendErrorResponse(rc, cause, statusCode, errorCode, null);
        })
      .add(
        TenantNotEnabledException.class, (cause, rc) ->
          sendErrorResponse(rc, cause, BAD_REQUEST, ErrorCode.UNKNOWN_TENANT, null))
      .addDefault((cause, rc) ->
        sendErrorResponse(rc, cause, INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_ERROR, null));
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

  private static final class ErrHandler {

    private final Predicate<Throwable> predicate;
    private final BiConsumer<Throwable, RoutingContext> consumer;
    private ErrHandler next;

    ErrHandler(Predicate<Throwable> predicate, BiConsumer<Throwable, RoutingContext> consumer) {
      this.predicate = predicate;
      this.consumer = consumer;
    }

    void handle(Throwable throwable, RoutingContext routingContext) {
      requireNonNull(throwable, "Throwable cannot be null");
      requireNonNull(routingContext, "Routing context cannot be null");

      if (predicate.test(throwable)) {
        consumer.accept(throwable, routingContext);
      } else if (next != null) {
        next.handle(throwable, routingContext);
      } else {
        log.warn("No handler defined for exception: type = {}, errMsg = {}",
          throwable.getClass(), throwable.getMessage());
      }
    }

    void setNext(ErrHandler next) {
      this.next = next;
    }

    static ErrHandlerBuilder builder() {
      return new ErrHandlerBuilder();
    }
  }

  private static final class ErrHandlerBuilder {
    private ErrHandler first;
    private ErrHandler last;

    ErrHandlerBuilder add(Predicate<Throwable> predicate, BiConsumer<Throwable, RoutingContext> consumer) {
      if (first == null) {
        first = last = new ErrHandler(predicate, consumer);
        return this;
      }

      var eh = new ErrHandler(predicate, consumer);
      last.setNext(eh);
      last = eh;

      return this;
    }

    ErrHandlerBuilder add(Class<? extends Throwable> throwableClass, BiConsumer<Throwable, RoutingContext> consumer) {
      return add(is(throwableClass), consumer);
    }

    ErrHandler addDefault(BiConsumer<Throwable, RoutingContext> consumer) {
      add(throwable -> true, consumer);
      return first;
    }

    private static Predicate<Throwable> is(Class<? extends Throwable> clazz) {
      return clazz::isInstance;
    }
  }
}
