package org.folio.sidecar.service.routing.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.service.ErrorHandler;

@RequiredArgsConstructor
public class ScRequestHandler implements Handler<RoutingContext> {

  private final ChainedHandler handler;
  private final ErrorHandler errorHandler;

  @Override
  public void handle(RoutingContext rc) {
    try {
      rc.put("rt", System.currentTimeMillis());
      handler.handle(rc);
    } catch (Exception error) {
      errorHandler.sendErrorResponse(rc, error);
    }
  }
}
