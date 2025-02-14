package org.folio.sidecar.service.routing.handler;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.routing.lookup.RoutingLookup;
import org.folio.sidecar.utils.RoutingUtils;

@RequiredArgsConstructor
public class RoutingHandlerWithLookup implements ChainedHandler {

  private final RoutingLookup routingLookup;
  private final RoutingEntryHandler handler;
  private final PathProcessor pathProcessor;
  private final ErrorHandler errorHandler;

  @Override
  public Future<Boolean> handle(RoutingContext rc) {
    var path = pathProcessor.cleanIngressRequestPath(rc.request().path());
    var routing = routingLookup.lookupRoute(path, rc);

    return routing.map(foundEntry -> foundEntry
      .map(handleRoutingEntry(rc))
      .orElse(false)
      ).onFailure(error -> errorHandler.sendErrorResponse(rc, error));
  }

  private Function<ScRoutingEntry, Boolean> handleRoutingEntry(RoutingContext rc) {
    return routingEntry -> {
      RoutingUtils.putScRoutingEntry(rc, routingEntry);

      handler.handle(routingEntry, rc);

      return true;
    };
  }
}
