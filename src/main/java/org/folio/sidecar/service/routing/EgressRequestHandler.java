package org.folio.sidecar.service.routing;

import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.folio.sidecar.model.ScRoutingEntry.GATEWAY_INTERFACE_ID;
import static org.folio.sidecar.utils.RoutingUtils.hasHeaderWithValue;
import static org.folio.sidecar.utils.RoutingUtils.hasUserIdHeader;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.ServiceTokenProvider;
import org.folio.sidecar.service.SystemUserTokenProvider;
import org.folio.sidecar.service.filter.RequestFilterService;
import org.folio.sidecar.utils.RoutingUtils;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class EgressRequestHandler implements RequestHandler {

  private final ErrorHandler errorHandler;
  private final PathProcessor pathProcessor;
  private final RequestFilterService requestFilterService;
  private final RequestForwardingService requestForwardingService;
  private final ServiceTokenProvider tokenProvider;
  private final SystemUserTokenProvider systemUserService;

  /**
   * Handles outgoing (egress) request.
   *
   * @param rc - {@link RoutingContext} object to handle
   */
  @Override
  public void handle(RoutingContext rc, ScRoutingEntry routingEntry) {
    var rq = rc.request();
    log.info("Handling egress request [method: {}, path: {}, requestId: {}, sc-request-id: {}]",
      rq.method(), rq.path(), rq.getHeader(REQUEST_ID), rc.get("sc-req-id"));

    requestFilterService.filterEgressRequest(rc)
      .compose(v -> populateModuleIdHeader(rc, routingEntry))
      .compose(v -> populateSystemToken(rc))
      .compose(v -> populateSystemUserToken(rc))
      .onSuccess(v -> forwardEgressRequest(rc, routingEntry))
      .onFailure(error -> errorHandler.sendErrorResponse(rc, error));
  }

  private Future<Void> populateSystemUserToken(RoutingContext routingContext) {
    if (!requireSystemUserToken(routingContext)) {
      return succeededFuture();
    }

    var tenantName = RoutingUtils.getTenant(routingContext);

    return systemUserService.getToken(tenantName)
      .compose(token -> {
        setSysUserTokenIfAvailable(routingContext, token);
        return succeededFuture();
      }, error -> succeededFuture()); // any errors are ignored
  }

  /**
   * When handling egress calls, look for the presence of the X-Okapi-User-Id and X-Okapi-Token headers. If present
   * handle as usual. If either is missing, set these request headers to the id of the system user and the cached access
   * token.
   *
   * @param rc routing context
   * @param rq egress request
   * @param routingEntry entry for request forwarding
   */
  private void authenticateAndForwardRequest(RoutingContext rc, HttpServerRequest rq, ScRoutingEntry routingEntry) {
    log.info("Authenticating and forwarding egress request [method: {}, path: {}, requestId: {}, sc-request-id: {}]",
      rq.method(), rq.path(), rq.getHeader(REQUEST_ID), rc.get("sc-req-id"));
    var updatedPath = pathProcessor.cleanIngressRequestPath(rc.request().path());
    var serviceToken = tokenProvider.getServiceTokenSync(rc);
    RoutingUtils.setHeader(rc, OkapiHeaders.SYSTEM_TOKEN, serviceToken);

    if (requireSystemUserToken(rc)) {
      var tenantName = RoutingUtils.getTenant(rc);
      var systemUserToken = systemUserService.getTokenSync(tenantName);
      setSysUserTokenIfAvailable(rc, systemUserToken);
    }

    forwardRequest(rc, rq, routingEntry, updatedPath);
  }

  private void forwardEgressRequest(RoutingContext rc, ScRoutingEntry routingEntry) {
    var rq = rc.request();
    var updatedPath = pathProcessor.cleanIngressRequestPath(rc.request().path());

    log.info("Forwarding egress request to module: [method: {}, path: {}, moduleId: {}, url: {}]",
      rq.method(), updatedPath, routingEntry.getModuleId(), routingEntry.getLocation());
    if (GATEWAY_INTERFACE_ID.equals(routingEntry.getInterfaceId())) {
      requestForwardingService.forwardToGateway(rc, routingEntry.getLocation() + updatedPath);
    } else {
      requestForwardingService.forwardEgress(rc, routingEntry.getLocation() + updatedPath);
    }
  }

  private boolean requireSystemUserToken(RoutingContext rc) {
    return !hasUserIdHeader(rc) || !hasHeaderWithValue(rc, OkapiHeaders.TOKEN, true);
  }

  private static void setSysUserTokenIfAvailable(RoutingContext rc, String token) {
    if (isNotBlank(token)) {
      RoutingUtils.setHeader(rc, OkapiHeaders.TOKEN, token);
      // appropriate user id will be put from token by a sidecar when handling ingress request
      rc.request().headers().remove(OkapiHeaders.USER_ID);
    }
  }
}
