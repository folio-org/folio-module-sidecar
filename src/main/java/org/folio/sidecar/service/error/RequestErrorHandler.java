package org.folio.sidecar.service.error;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RequestErrorHandler {

  public void handleBadRequest(RoutingContext context, String message) {
    context.response()
      .setStatusCode(400)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject()
        .put("error", "Bad Request")
        .put("message", message)
        .encode());
  }
}