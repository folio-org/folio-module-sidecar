package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
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
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.configuration.properties.ModuleProperties;
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
class EgressRequestHandler implements RoutingEntryHandler {

  private final PathProcessor pathProcessor;
  private final RequestFilterService requestFilterService;
  private final RequestForwardingService requestForwardingService;
  private final ServiceTokenProvider serviceTokenProvider;
  private final SystemUserTokenProvider systemUserTokenProvider;
  private final ModuleProperties moduleProperties;
  private final boolean ignoreGettingSystemUserTokenError;

  EgressRequestHandler(PathProcessor pathProcessor, RequestFilterService requestFilterService,
      RequestForwardingService requestForwardingService, ServiceTokenProvider serviceTokenProvider,
      SystemUserTokenProvider systemUserTokenProvider, ModuleProperties moduleProperties,
      @ConfigProperty(name = "handler.egress.ignore-system-user-token-error",
          defaultValue = "false") boolean ignoreGettingSystemUserTokenError) {
    this.pathProcessor = pathProcessor;
    this.requestFilterService = requestFilterService;
    this.requestForwardingService = requestForwardingService;
    this.serviceTokenProvider = serviceTokenProvider;
    this.systemUserTokenProvider = systemUserTokenProvider;
    this.moduleProperties = moduleProperties;
    this.ignoreGettingSystemUserTokenError = ignoreGettingSystemUserTokenError;
  }

  /**
   * Handles outgoing (egress) request.
   *
   * @param rc - {@link RoutingContext} object to handle
   */
  @Override
  public Future<Void> handle(ScRoutingEntry routingEntry, RoutingContext rc) {
    var rq = rc.request();
    log.debug("Handling egress request [method: {}, uri: {}, requestId: {}]",
        rq::method, dumpUri(rc), () -> rq.getHeader(REQUEST_ID));

    // Detect potential misconfiguration: public traffic routed to sidecar
    var hasSidecarSignature = rc.get("SELF_REQUEST_KEY");
    if (hasSidecarSignature == null || Boolean.FALSE.equals(hasSidecarSignature)) {
      log.warn("POTENTIAL MISCONFIGURATION: Egress handler processing request without sidecar signature!");
      log.warn("This might indicate Kong/gateway is routing public traffic to sidecar port instead of main app.");
      log.warn("Request: {} {}", rq.method(), rq.path());
      log.warn("See: https://folio-org.atlassian.net/browse/RANCHER-2623");
    }

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
            .map(setSysUserToken(rc))
            .recover(err -> {
              if (!ignoreGettingSystemUserTokenError) {
                return failedFuture(err);
              } else {
                log.debug("Failed to get system user token: {}.\n"
                    + "The error is ignored because 'ignoreGettingSystemUserTokenError' is true", err.getMessage(),
                    err);
                return succeededFuture();
              }
            });
  }

  private boolean requireSystemUserToken(RoutingContext rc) {
    return !hasHeaderWithValue(rc, OkapiHeaders.TOKEN, true);
  }

  private Future<Void> forwardEgressRequest(RoutingContext rc, ScRoutingEntry routingEntry) {
    var rq = rc.request();
    var updatedPath = pathProcessor.cleanIngressRequestPath(rc.request().path());

    log.debug("Forwarding egress request to module: [method: {}, uri: {}, moduleId: {}, url: {}]",
        rq::method, dumpUri(rc), routingEntry::getModuleId, routingEntry::getLocation);

    return (GATEWAY_INTERFACE_ID.equals(routingEntry.getInterfaceId()))
        ? requestForwardingService.forwardToGateway(rc, routingEntry.getLocation() + updatedPath)
        : requestForwardingService.forwardEgress(rc, routingEntry.getLocation() + updatedPath);
  }

  private Function<Optional<String>, Void> setSysUserToken(RoutingContext rc) {
    return t -> {
      var token = t.orElseThrow(() -> new BadRequestException("System user token is required"
          + " if the request doesn't contain " + OkapiHeaders.TOKEN
          + ". Check that system user is configured for the module: " + moduleProperties.getId()));

      String requestId = rc.request().getHeader(REQUEST_ID);

      RoutingUtils.setHeader(rc, OkapiHeaders.TOKEN, token);
      log.debug("System user token assigned to {} header: token = {} [requestId: {}]",
          () -> OkapiHeaders.TOKEN, () -> tokenHash(token), () -> requestId);

      return null;
    };
  }
}
