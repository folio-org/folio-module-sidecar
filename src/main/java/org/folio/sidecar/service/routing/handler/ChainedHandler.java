package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Consumer;

public interface ChainedHandler {

  Future<Boolean> handle(RoutingContext rc);

  default ChainedHandler next(ChainedHandler next) {
    return rc -> handle(rc).compose(result -> result ? succeededFuture(true) : next.handle(rc));
  }

  static ChainedHandler as(Consumer<RoutingContext> consumer) {
    return rc -> {
      consumer.accept(rc);
      return succeededFuture(true);
    };
  }
}
