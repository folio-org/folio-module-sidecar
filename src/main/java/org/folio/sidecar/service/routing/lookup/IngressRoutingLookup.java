package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.calculateRoutes;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.getRoutes;
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
public class IngressRoutingLookup implements RoutingLookup {

  private Map<String, List<ScRoutingEntry>> ingressRequestCache = new HashMap<>();

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Searching routing entries for ingress request: method [{}], uri [{}]", request::method, dumpUri(rc));

    var entry = lookup(request, path, ingressRequestCache, false);

    log.debug("Ingress entry found: {}", entry);

    return succeededFuture(entry);
  }

  // TODO (Dima Tkachenko): review code
  public void bootstrapModule(ModuleBootstrap bootstrap) {
    log.info("Initializing Ingress module routes from bootstrap information");

    ingressRequestCache = getRoutes(bootstrap.getModule());

    log.info("Ingress routes initialized: count = {}", () -> calculateRoutes(ingressRequestCache));
  }

  public void updateIngressRoutes(ModuleBootstrapDiscovery discovery) {
    log.info("Updating module ingress routes");

    ingressRequestCache = getRoutes(discovery);

    log.info("Ingress routes updated [count = {}]", () -> calculateRoutes(ingressRequestCache));
  }
}
