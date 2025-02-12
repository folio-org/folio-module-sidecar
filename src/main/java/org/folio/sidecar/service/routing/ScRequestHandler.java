package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.routing.lookup.RoutingLookup;
import org.folio.sidecar.service.routing.lookup.RoutingLookupUtils;
import org.folio.sidecar.utils.RoutingUtils;

public class ScRequestHandler implements Handler<RoutingContext> {

  private final ChainedHandler handler;
  private final PathProcessor pathProcessor;
  private final ErrorHandler errorHandler;

  public ScRequestHandler(IngressRequestHandler ingressRequestHandler, EgressRequestHandler egressRequestHandler,
                          RoutingLookupUtils requestMatchingService, ErrorHandler errorHandler, PathProcessor pathProcessor) {
    this.errorHandler = errorHandler;
    this.pathProcessor = pathProcessor;

    /*this.handler = RoutingHandler.ingress(requestMatchingService, ingressRequestHandler)
      .next(RoutingHandler.egress(requestMatchingService, egressRequestHandler))
      .next(notFound());*/

    this.handler = RoutingHandler.simple(requestMatchingService::lookupForIngressRequest, ingressRequestHandler, pathProcessor)
      .next(RoutingHandler.simple(requestMatchingService::lookupForEgressRequest, egressRequestHandler, pathProcessor))
      .next(notFound());
  }

  @Override
  public void handle(RoutingContext rc) {
    try {
      rc.put("rt", System.currentTimeMillis());
      handler.handle(rc);
    } catch (Exception error) {
      errorHandler.sendErrorResponse(rc, error);
    }
  }

  private ChainedHandler notFound() {
    return ChainedHandler.as(rc -> errorHandler.sendErrorResponse(rc, notFoundError(rc.request())));
  }

  private static NotFoundException notFoundError(HttpServerRequest rq) {
    return new NotFoundException(format("Route is not found [method: %s, path: %s]", rq.method(), rq.path()));
  }

  interface ChainedHandler {

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

  @RequiredArgsConstructor(staticName = "of")
  private static final class RoutingHandler implements ChainedHandler {

    private final RoutingLookup routingLookup;
    private final RequestHandler handler;
    private final PathProcessor pathProcessor;

    static RoutingHandler simple(BiFunction<String, RoutingContext, Optional<ScRoutingEntry>> simpleLookup,
      RequestHandler handler, PathProcessor pathProcessor) {
      return of((path, rc) -> succeededFuture(simpleLookup.apply(path, rc)), handler, pathProcessor);
    }

    @Override
    public Future<Boolean> handle(RoutingContext rc) {
      var path = pathProcessor.cleanIngressRequestPath(rc.request().path());
      var routing = routingLookup.lookupRoute(path, rc);

      return routing.map(entry -> {
        if (entry.isPresent()) {
          ScRoutingEntry routingEntry = entry.get();
          RoutingUtils.putScRoutingEntry(rc, routingEntry);

          handler.handle(rc, routingEntry);
          return true;
        } else {
          return false;
        }
      });
    }
  }
}
