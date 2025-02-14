package org.folio.sidecar.service.routing.handler;

import static java.lang.String.format;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.ws.rs.NotFoundException;
import org.folio.sidecar.service.ErrorHandler;

public class NotFoundHandler {

  @Produces
  @Named("notFoundHandler")
  @ApplicationScoped
  public static ChainedHandler getInstance(ErrorHandler errorHandler) {
    return ChainedHandler.as(rc -> errorHandler.sendErrorResponse(rc, notFoundError(rc.request())));
  }

  private static NotFoundException notFoundError(HttpServerRequest rq) {
    return new NotFoundException(format("Route is not found [method: %s, path: %s]", rq.method(), rq.path()));
  }
}
