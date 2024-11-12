package org.folio.sidecar.service.routing;

import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.model.ScRoutingEntry.GATEWAY_INTERFACE_ID;

import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.ServiceTokenProvider;
import org.folio.sidecar.service.SystemUserTokenProvider;
import org.folio.sidecar.service.filter.EgressRequestFilter;
import org.folio.sidecar.utils.CollectionUtils;
import org.folio.sidecar.utils.RoutingUtils;

@Log4j2
@ApplicationScoped
public class EgressRequestHandler implements RequestHandler {

  private final ErrorHandler errorHandler;
  private final PathProcessor pathProcessor;
  private final List<EgressRequestFilter> requestFilters;
  private final RequestForwardingService requestForwardingService;
  private final ServiceTokenProvider tokenProvider;
  private final SystemUserTokenProvider systemUserService;

  /**
   * Injects dependencies from quarkus context.
   *
   * @param errorHandler - {@link ErrorHandler} component
   * @param requestForwardingService - {@link RequestForwardingService} component
   * @param filters - iterable {@link Instance} of {@link EgressRequestFilter} components
   * @param tokenProvider - Keycloak system token provider
   * @param systemUserService - System user service
   */
  @Inject
  public EgressRequestHandler(ErrorHandler errorHandler, PathProcessor pathProcessor,
    RequestForwardingService requestForwardingService, Instance<EgressRequestFilter> filters,
    ServiceTokenProvider tokenProvider, SystemUserTokenProvider systemUserService) {
    this.errorHandler = errorHandler;
    this.pathProcessor = pathProcessor;
    this.requestForwardingService = requestForwardingService;
    this.requestFilters = CollectionUtils.sortByOrder(filters);
    this.tokenProvider = tokenProvider;
    this.systemUserService = systemUserService;
  }

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

    requestFilters.forEach(filter -> filter.filter(rc));
    if (rc.response().ended()) {
      log.debug("Filter validation failed, error has been sent [method: {}, path: {}]", rq.method(), rq.path());
      return;
    }

    var moduleId = routingEntry.getModuleId();
    if (routingEntry.getLocation() == null) {
      var errorMessage = "Module location is not found for moduleId: " + moduleId;
      errorHandler.sendErrorResponse(rc, new BadRequestException(errorMessage));
      return;
    }

    RoutingUtils.setHeader(rc, OkapiHeaders.MODULE_ID, moduleId);

    authenticateAndForwardRequest(rc, rq, routingEntry);
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
    var serviceToken = tokenProvider.getServiceToken(rc);

    RoutingUtils.setHeader(rc, OkapiHeaders.SYSTEM_TOKEN, serviceToken);

    if (requireSystemUserToken(rc)) {
      log.info("System user token branch entered [requestId: {}, sc-request-id: {}]",
        rq.getHeader(REQUEST_ID), rc.get("sc-req-id"));

      var tenantName = RoutingUtils.getTenant(rc);
      var sysUserToken = systemUserService.getToken(tenantName);
      RoutingUtils.setHeader(rc, OkapiHeaders.TOKEN, sysUserToken);
      rc.request().headers().remove(OkapiHeaders.USER_ID);
    }

    forwardRequest(rc, rq, routingEntry, updatedPath);
  }

  private boolean requireSystemUserToken(RoutingContext rc) {
    return !RoutingUtils.hasUserIdHeader(rc) || !RoutingUtils.hasHeader(rc, OkapiHeaders.TOKEN);
  }

  private void forwardRequest(RoutingContext rc, HttpServerRequest rq, ScRoutingEntry routingEntry,
    String updatedPath) {
    log.info("Forwarding egress request to module: [method: {}, path: {}, moduleId: {}, url: {}]",
      rq.method(), updatedPath, routingEntry.getModuleId(), routingEntry.getLocation());
    if (GATEWAY_INTERFACE_ID.equals(routingEntry.getInterfaceId())) {
      requestForwardingService.forwardToGateway(rc, routingEntry.getLocation() + updatedPath);
    } else {
      requestForwardingService.forwardEgress(rc, routingEntry.getLocation() + updatedPath);
    }
  }

  private static void setSysUserTokenIfAvailable(RoutingContext rc, AsyncResult<String> tokenResult) {
    if (tokenResult.succeeded()) {
      var token = tokenResult.result();
      RoutingUtils.setHeader(rc, OkapiHeaders.TOKEN, token);
      // appropriate user id will be put from token by a sidecar when handling ingress request
      rc.request().headers().remove(OkapiHeaders.USER_ID);
    }
  }
}
