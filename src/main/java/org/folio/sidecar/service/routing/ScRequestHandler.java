package org.folio.sidecar.service.routing;

import static java.lang.String.format;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;

public class ScRequestHandler implements Handler<RoutingContext> {

  private final ChainedHandler handler;
  private final ErrorHandler errorHandler;

  public ScRequestHandler(IngressRequestHandler ingressRequestHandler, EgressRequestHandler egressRequestHandler,
    RequestMatchingService requestMatchingService, ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;

    this.handler = RoutingHandler.ingress(requestMatchingService, ingressRequestHandler)
      .next(RoutingHandler.egress(requestMatchingService, egressRequestHandler))
      .next(notFound());
  }

  @Override
  public void handle(RoutingContext rc) {
    try {
      rc.put("sc-req-id", UUID.randomUUID().toString());
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

    boolean handle(RoutingContext rc);

    default ChainedHandler next(ChainedHandler next) {
      return rc -> handle(rc) || next.handle(rc);
    }

    static ChainedHandler as(Consumer<RoutingContext> consumer) {
      return rc -> {
        consumer.accept(rc);
        return true;
      };
    }
  }

  @RequiredArgsConstructor
  private static final class RoutingHandler implements ChainedHandler {

    private final Function<RoutingContext, Optional<ScRoutingEntry>> routingLookup;
    private final RequestHandler handler;

    @Override
    public boolean handle(RoutingContext rc) {
      var routing = routingLookup.apply(rc);

      if (routing.isPresent()) {
        handler.handle(rc, routing.get());
        return true;
      } else {
        return false;
      }
    }

    static RoutingHandler ingress(RequestMatchingService requestMatchingService, IngressRequestHandler requestHandler) {
      return new RoutingHandler(requestMatchingService::lookupForIngressRequest, requestHandler);
    }

    static RoutingHandler egress(RequestMatchingService requestMatchingService, EgressRequestHandler requestHandler) {
      return new RoutingHandler(requestMatchingService::lookupForEgressRequest, requestHandler);
    }
  }
}
