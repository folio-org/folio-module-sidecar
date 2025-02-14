package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getCollectedRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.lookup;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.model.ScRoutingEntry;

@Log4j2
public class EgressRoutingLookup implements RoutingLookup {

  private Map<String, List<ScRoutingEntry>> egressRequestCache = new HashMap<>();

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for egress request: method [{}], uri [{}]", request::method, dumpUri(rc));

    var entry = lookup(request, path, egressRequestCache, true);

    log.debug("Egress entry found: {}", entry);

    return succeededFuture(entry);
  }

  // TODO (Dima Tkachenko): review code
  public void bootstrapModule(ModuleBootstrap bootstrap) {
    log.info("Initializing Egress module routes from bootstrap information");

    egressRequestCache = getCollectedRoutes(bootstrap.getRequiredModules());

    log.info("Egress routes initialized: count = {}", () -> calculateRoutes(egressRequestCache));
  }

  public void updateEgressRoutes(List<ModuleBootstrapDiscovery> discoveries) {
    log.info("Updating module egress routes");

    egressRequestCache = getCollectedRoutes(discoveries);

    log.info("Egress routes updated [count = {}]", () -> calculateRoutes(egressRequestCache));
  }
}
