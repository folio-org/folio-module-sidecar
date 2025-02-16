package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getRoutes;
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
@Named("ingressLookup")
@ApplicationScoped
public class IngressRoutingLookup implements RoutingLookup, ModuleBootstrapListener {

  private Map<String, List<ScRoutingEntry>> ingressRequestCache = new HashMap<>();

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for ingress request: method [{}], uri [{}]", request::method, dumpUri(rc));

    var entry = lookup(request, path, ingressRequestCache, false);

    log.debug("Ingress entry found: {}", entry);

    return succeededFuture(entry);
  }

  @Override
  public void onModuleBootstrap(ModuleBootstrapDiscovery moduleBootstrap) {
    log.info("Updating module ingress routes");

    ingressRequestCache = getRoutes(moduleBootstrap);

    log.info("Ingress routes updated: count = {}", () -> calculateRoutes(ingressRequestCache));
  }

  @Override
  public void onRequiredModulesBootstrap(List<ModuleBootstrapDiscovery> requiredModulesBootstrap) {
  }
}
