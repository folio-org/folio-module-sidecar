package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.util.Strings.isNotBlank;
import static org.folio.sidecar.model.ScRoutingEntry.GATEWAY_INTERFACE_ID;
import static org.folio.sidecar.utils.RoutingUtils.hasHeaderWithValue;
import static org.folio.sidecar.utils.RoutingUtils.hasUserIdHeader;

import io.vertx.core.Future;
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
    log.info("Handling egress request [method: {}, path: {}]", rq.method(), rq.path());

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
      });
  }

  private Future<Void> populateModuleIdHeader(RoutingContext routingContext, ScRoutingEntry routingEntry) {
    var moduleId = routingEntry.getModuleId();
    
    if (routingEntry.getLocation() == null) {
      return failedFuture(new BadRequestException("Module location is not found for moduleId: " + moduleId));
    } else {
      RoutingUtils.setHeader(routingContext, OkapiHeaders.MODULE_ID, moduleId);
      return succeededFuture();
    }
  }

  private Future<Void> populateSystemToken(RoutingContext routingContext) {
    return tokenProvider.getServiceToken(routingContext)
      .compose(serviceToken -> {
        RoutingUtils.setHeader(routingContext, OkapiHeaders.SYSTEM_TOKEN, serviceToken);
        return succeededFuture();
      });
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
