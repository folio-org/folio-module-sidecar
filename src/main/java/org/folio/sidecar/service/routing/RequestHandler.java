package org.folio.sidecar.service.routing;

import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.model.ScRoutingEntry;

public interface RequestHandler {

  /**
   * Provides ability to handle incoming request iteratively.
   *
   * @param rc - {@link RoutingContext} object with all incoming request information.
   * @param routingEntry - matched routing entry from {@code mgr-applications}
   */
  void handle(RoutingContext rc, ScRoutingEntry routingEntry);
}
