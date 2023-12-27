package org.folio.sidecar.service.routing;

import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.ServiceTokenProvider;
import org.folio.sidecar.service.SystemUserTokenProvider;
import org.folio.sidecar.service.filter.EgressRequestFilter;
import org.folio.sidecar.utils.CollectionUtils;
import org.folio.sidecar.utils.RoutingUtils;

@Log4j2
@ApplicationScoped
public class EgressRequestHandler implements RequestHandler {

  @ConfigProperty(name = "sidecar.module-path-prefix.enabled") boolean modulePrefixEnabled;

  private final ErrorHandler errorHandler;
  private final List<EgressRequestFilter> requestFilters;
  private final RequestForwardingService requestForwardingService;
  private final ServiceTokenProvider tokenProvider;
  private final SystemUserTokenProvider systemUserService;
  private final ModuleProperties moduleProperties;

  /**
   * Injects dependencies from quarkus context.
   *
   * @param errorHandler - {@link ErrorHandler} component
   * @param requestForwardingService - {@link RequestForwardingService} component
   * @param filters - iterable {@link Instance} of {@link EgressRequestFilter} components
   * @param tokenProvider - Keycloak system token provider
   * @param systemUserService - System user service
   * @param moduleProperties - Information about the module
   */
  @Inject
  public EgressRequestHandler(
    ErrorHandler errorHandler,
    RequestForwardingService requestForwardingService,
    Instance<EgressRequestFilter> filters,
    ServiceTokenProvider tokenProvider,
    SystemUserTokenProvider systemUserService,
    ModuleProperties moduleProperties) {
    this.errorHandler = errorHandler;
    this.requestForwardingService = requestForwardingService;
    this.requestFilters = CollectionUtils.sortByOrder(filters);
    this.tokenProvider = tokenProvider;
    this.systemUserService = systemUserService;
    this.moduleProperties = moduleProperties;
  }

  /**
   * Handles outgoing (egress) request.
   *
   * @param rc - {@link RoutingContext} object to handle
   */
  @Override
  public void handle(RoutingContext rc, ScRoutingEntry routingEntry) {
    var rq = rc.request();
    log.info("Handling egress request [method: {}, path: {}]", rq.method(), rq.path());

    requestFilters.forEach(filter -> filter.filter(rc));
    if (rc.response().ended()) {
      log.debug("Filter validation failed, error has been sent [method: {}, path: {}]", rq.method(), rq.path());
      return;
    }

    var moduleId = routingEntry.getModuleId();
    var url = routingEntry.getLocation();
    if (url == null) {
      var errorMessage = "Module location is not found for moduleId: " + moduleId;
      errorHandler.sendErrorResponse(rc, new BadRequestException(errorMessage));
      return;
    }

    RoutingUtils.setHeader(rc, OkapiHeaders.MODULE_ID, moduleId);

    authenticateAndForwardRequest(rc, rq, moduleId, url);
  }

  /**
   * When handling egress calls, look for the presence of the X-Okapi-User-Id and X-Okapi-Token headers. If present
   * handle as usual. If either is missing, set these request headers to the id of the system user and the cached access
   * token.
   *
   * @param rc routing context
   * @param rq egress request
   * @param moduleId module identifier for request forwarding
   * @param url url for request forwarding
   */
  private void authenticateAndForwardRequest(RoutingContext rc, HttpServerRequest rq, String moduleId, String url) {
    var updatedPath = RoutingUtils.updatePath(rc, modulePrefixEnabled, moduleProperties.getName());
    tokenProvider.getServiceToken(rc)
      .onSuccess(serviceToken -> {

        RoutingUtils.setHeader(rc, OkapiHeaders.SYSTEM_TOKEN, serviceToken);

        if (requireSystemUserToken(rc)) {
          var tenantName = RoutingUtils.getTenant(rc);
          systemUserService.getToken(tenantName)
            .onComplete(token -> setSysUserTokenIfAvailable(rc, token))
            .andThen(token -> forwardRequest(rc, rq, moduleId, url, updatedPath));
          return;
        }

        forwardRequest(rc, rq, moduleId, url, updatedPath);
      });
  }

  private boolean requireSystemUserToken(RoutingContext rc) {
    return !RoutingUtils.hasUserIdHeader(rc) || !RoutingUtils.hasHeader(rc, OkapiHeaders.TOKEN);
  }

  private void forwardRequest(RoutingContext rc, HttpServerRequest rq, String moduleId, String url,
    String updatedPath) {
    log.info("Forwarding egress request to module: [method: {}, path: {}, moduleId: {}, url: {}]",
      rq.method(), updatedPath, moduleId, url);
    requestForwardingService.forward(rc, url + updatedPath);
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
