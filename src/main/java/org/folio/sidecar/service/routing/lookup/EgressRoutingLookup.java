package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getCollectedRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.lookup;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.routing.ModuleBootstrapListener;

@Log4j2
@Named("egressLookup")
@ApplicationScoped
public class EgressRoutingLookup implements RoutingLookup, ModuleBootstrapListener {

  private Map<String, List<ScRoutingEntry>> egressRequestCache = new HashMap<>();

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for egress request: method [{}], uri [{}]", request::method, dumpUri(rc));

    var entry = lookup(request, path, egressRequestCache, true);

    log.debug("Egress entry found: {}", entry);

    return succeededFuture(entry);
  }

  @Override
  public void onRequiredModulesBootstrap(List<ModuleBootstrapDiscovery> requiredModulesBootstrap,
    ChangeType changeType) {
    log.info("{} module egress routes", changeType == INIT ? "Initializing" : "Updating");

    egressRequestCache = getCollectedRoutes(requiredModulesBootstrap);

    log.info("Egress routes {}: count = {}", () -> changeType == INIT ? "initialized" : "updated",
      () -> calculateRoutes(egressRequestCache));
  }
}
