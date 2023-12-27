package org.folio.sidecar.service.filter;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

public interface RequestFilter {

  /**
   * Filters outgoing request.
   *
   * @param routingContext - {@link RoutingContext} object to filter
   */
  Future<RoutingContext> filter(RoutingContext routingContext);

  /**
   * Filters outgoing request.
   *
   * @param routingContext - {@link RoutingContext} object to filter
   */
  default Future<RoutingContext> applyFilter(RoutingContext routingContext) {
    return filter(routingContext);
  }
}
