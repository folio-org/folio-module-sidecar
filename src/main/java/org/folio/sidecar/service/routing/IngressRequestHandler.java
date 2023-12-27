package org.folio.sidecar.service.routing;

import static java.lang.Boolean.TRUE;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.filter.RequestFilterService;
import org.folio.sidecar.utils.RoutingUtils;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class IngressRequestHandler implements RequestHandler {

  private final ErrorHandler errorHandler;
  private final ModuleProperties moduleProperties;
  private final SidecarProperties sidecarProperties;
  private final RequestForwardingService requestForwardingService;
  private final RequestFilterService requestFilterService;

  /**
   * Handles incoming (ingress) request using given {@link RoutingContext} object.
   *
   * @param routingContext - routing context to handle
   */
  @Override
  public void handle(RoutingContext routingContext, ScRoutingEntry scRoutingEntry) {
    var rq = routingContext.request();
    log.info("Handling ingress request [method: {}, path: {}]", rq.method(), rq.path());
    requestFilterService.filterIngressRequest(routingContext)
      .onSuccess(authResponse -> forwardRequest(routingContext))
      .onFailure(error -> errorHandler.sendErrorResponse(routingContext, error));
  }

  private void forwardRequest(RoutingContext rc) {
    var request = rc.request();

    var headers = request.headers();
    headers.set(OkapiHeaders.URL, sidecarProperties.getUrl());
    headers.set(OkapiHeaders.MODULE_ID, moduleProperties.getId());

    var addModuleNameToPath = TRUE.equals(rc.get(RoutingUtils.ADD_MODULE_NAME_TO_PATH_KEY));
    var path = RoutingUtils.buildPathWithPrefix(rc, addModuleNameToPath, moduleProperties.getName());
    log.info("Forwarding ingress request to underlying module: [method: {}, path: {}]", request.method(), path);

    var absUri = moduleProperties.getUrl() + path;
    requestForwardingService.forward(rc, absUri);
  }
}
