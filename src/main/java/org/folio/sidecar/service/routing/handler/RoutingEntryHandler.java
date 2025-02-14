package org.folio.sidecar.service.routing.handler;

import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.model.ScRoutingEntry;

public interface RoutingEntryHandler {

  /**
   * Provides ability to handle incoming request iteratively.
   *
   * @param routingEntry - matched routing entry from {@code mgr-applications}
   * @param rc           - {@link RoutingContext} object with all incoming request information.
   */
  void handle(ScRoutingEntry routingEntry, RoutingContext rc);
}
