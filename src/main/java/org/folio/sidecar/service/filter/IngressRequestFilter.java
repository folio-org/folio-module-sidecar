package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.support.Ordered;

public interface IngressRequestFilter extends Ordered, RequestFilter {

  /**
   * Filters incoming request.
   *
   * @param routingContext - {@link RoutingContext} object to filter
   */
  @Override
  default Future<RoutingContext> applyFilter(RoutingContext routingContext) {
    return shouldSkip(routingContext) ? succeededFuture(routingContext) : filter(routingContext);
  }

  /**
   * Checks if request should be skipped from filtering.
   *
   * @param routingContext routing context
   * @return true if request should be skipped by the filter.
   */
  default boolean shouldSkip(RoutingContext routingContext) {
    return false;
  }
}
