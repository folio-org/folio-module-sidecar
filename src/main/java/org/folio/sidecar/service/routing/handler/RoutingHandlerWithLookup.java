package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.routing.lookup.RoutingLookup;
import org.folio.sidecar.utils.RoutingUtils;

@RequiredArgsConstructor
public class RoutingHandlerWithLookup implements ChainedHandler {

  private final RoutingLookup routingLookup;
  private final RoutingEntryHandler handler;
  private final PathProcessor pathProcessor;

  @Override
  public Future<Boolean> handle(RoutingContext rc) {
    var path = pathProcessor.cleanIngressRequestPath(rc.request().path());

    return routingLookup.lookupRoute(path, rc)
      .compose(handleOrFalse(handleRoutingEntry(rc)));
  }

  private Function<ScRoutingEntry, Future<Boolean>> handleRoutingEntry(RoutingContext rc) {
    return routingEntry -> {
      RoutingUtils.putScRoutingEntry(rc, routingEntry);

      return handler.handle(routingEntry, rc).map(v -> true);
    };
  }

  private static Function<Optional<ScRoutingEntry>, Future<Boolean>> handleOrFalse(
    Function<ScRoutingEntry, Future<Boolean>> handler) {
    return foundEntry -> foundEntry
      .map(handler)
      .orElse(succeededFuture(false));
  }
}
