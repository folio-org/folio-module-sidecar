package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.model.ScRoutingEntry.GATEWAY_INTERFACE_ID;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;
import static org.folio.sidecar.utils.RoutingUtils.hasHeaderWithValue;
import static org.folio.sidecar.utils.TokenUtils.tokenHash;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.BadRequestException;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.filter.RequestFilterService;
import org.folio.sidecar.service.token.ServiceTokenProvider;
import org.folio.sidecar.service.token.SystemUserTokenProvider;
import org.folio.sidecar.utils.RoutingUtils;

@Log4j2
@Named
@ApplicationScoped
@RequiredArgsConstructor
class EgressRequestHandler implements RoutingEntryHandler {

  private final PathProcessor pathProcessor;
  private final RequestFilterService requestFilterService;
  private final RequestForwardingService requestForwardingService;
  private final ServiceTokenProvider serviceTokenProvider;
  private final SystemUserTokenProvider systemUserTokenProvider;

  /**
   * Handles outgoing (egress) request.
   *
   * @param rc - {@link RoutingContext} object to handle
   */
  @Override
  public Future<Void> handle(ScRoutingEntry routingEntry, RoutingContext rc) {
    var rq = rc.request();
    log.info("Handling egress request [method: {}, uri: {}, requestId: {}]",
      rq::method, dumpUri(rc), () -> rq.getHeader(REQUEST_ID));

    return requestFilterService.filterEgressRequest(rc)
      .map(v -> validateRoutingModuleId(routingEntry))
      .compose(v -> populateSystemToken(rc))
      .compose(v -> populateSystemUserToken(rc))
      .compose(v -> forwardEgressRequest(rc, routingEntry));
  }

  private Void validateRoutingModuleId(ScRoutingEntry routingEntry) {
    var moduleId = routingEntry.getModuleId();

    if (routingEntry.getLocation() == null) {
      throw new BadRequestException("Module location is not found for moduleId: " + moduleId);
    }
    return null;
  }

  private Future<Void> populateSystemToken(RoutingContext rc) {
    return serviceTokenProvider.getToken(rc)
      .map(serviceToken -> {
        RoutingUtils.setHeader(rc, OkapiHeaders.SYSTEM_TOKEN, serviceToken);

        log.debug("Service token assigned to {} header: token = {} [requestId: {}]",
          () -> OkapiHeaders.SYSTEM_TOKEN, () -> tokenHash(serviceToken), () -> rc.request().getHeader(REQUEST_ID));
        return null;
      });
  }

  private Future<Void> populateSystemUserToken(RoutingContext rc) {
    return !requireSystemUserToken(rc)
      ? succeededFuture()
      : systemUserTokenProvider.getToken(rc)
        .map(setSysUserTokenIfAvailable(rc))
        .otherwise(error -> {
          log.debug("Failed to get system user token: {}", error.getMessage(), error);
          return null;
        }); // any errors to get token are ignored
  }

  private boolean requireSystemUserToken(RoutingContext rc) {
    return !hasHeaderWithValue(rc, OkapiHeaders.TOKEN, true);
  }

  private Future<Void> forwardEgressRequest(RoutingContext rc, ScRoutingEntry routingEntry) {
    var rq = rc.request();
    var updatedPath = pathProcessor.cleanIngressRequestPath(rc.request().path());

    log.info("Forwarding egress request to module: [method: {}, uri: {}, moduleId: {}, url: {}]",
      rq::method, dumpUri(rc), routingEntry::getModuleId, routingEntry::getLocation);

    return (GATEWAY_INTERFACE_ID.equals(routingEntry.getInterfaceId()))
      ? requestForwardingService.forwardToGateway(rc, routingEntry.getLocation() + updatedPath)
      : requestForwardingService.forwardEgress(rc, routingEntry.getLocation() + updatedPath);
  }

  private static Function<String, Void> setSysUserTokenIfAvailable(RoutingContext rc) {
    return token -> {
      String requestId = rc.request().getHeader(REQUEST_ID);

      if (isNotBlank(token)) {
        RoutingUtils.setHeader(rc, OkapiHeaders.TOKEN, token);
        log.debug("System user token assigned to {} header: token = {} [requestId: {}]",
          () -> OkapiHeaders.TOKEN, () -> tokenHash(token), () -> requestId);
      } else {
        log.debug("System user token is not available [requestId: {}]", requestId);
      }
      return null;
    };
  }
}
