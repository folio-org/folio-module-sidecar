package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.sidecar.model.ScRoutingEntry.gatewayRoutingEntry;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class GatewayRoutingLookup implements RoutingLookup {

  private final SidecarProperties sidecarProperties;

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    var result = Optional.<ScRoutingEntry>empty();

    if (sidecarProperties.isForwardUnknownRequests()) {
      var request = rc.request();
      var moduleIdHeader = request.getHeader(OkapiHeaders.MODULE_ID);
      var unknownRequestsDestination = sidecarProperties.getUnknownRequestsDestination();

      log.warn("Egress routing entry was not found for the request's path. Forwarding request to the Gateway: "
          + "moduleId = {}, path = {}, destination = {}, x-okapi-module-id = {}",
        sidecarProperties.getName(), path, unknownRequestsDestination, moduleIdHeader);

      result = isBlank(moduleIdHeader)
        ? Optional.of(gatewayRoutingEntry(unknownRequestsDestination))
        : Optional.of(gatewayRoutingEntry(unknownRequestsDestination, moduleIdHeader));
    }

    return succeededFuture(result);
  }
}
