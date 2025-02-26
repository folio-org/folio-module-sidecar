package org.folio.sidecar.service.routing.lookup;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import org.folio.sidecar.model.ScRoutingEntry;

public interface RoutingLookup {

  Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc);
}
