package org.folio.sidecar.service.routing.handler;

import static java.lang.String.format;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.sidecar.service.ErrorHandler;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
class NotFoundHandler {

  @Named("notFoundHandler")
  @ApplicationScoped
  static ChainedHandler getInstance(ErrorHandler errorHandler) {
    return ChainedHandler.as(rc -> errorHandler.sendErrorResponse(rc, notFoundError(rc.request())));
  }

  private static NotFoundException notFoundError(HttpServerRequest rq) {
    return new NotFoundException(format("Route is not found [method: %s, path: %s]", rq.method(), rq.path()));
  }
}
