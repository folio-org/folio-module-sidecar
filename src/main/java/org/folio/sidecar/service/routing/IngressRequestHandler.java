package org.folio.sidecar.service.routing;

import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.filter.RequestFilterService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class IngressRequestHandler implements RequestHandler {

  private final ErrorHandler errorHandler;
  private final PathProcessor pathProcessor;
  private final ModuleProperties moduleProperties;
  private final SidecarProperties sidecarProperties;
  private final RequestForwardingService requestForwardingService;
  private final RequestFilterService requestFilterService;

  /**
   * Handles incoming (ingress) request using given {@link RoutingContext} object.
   *
   * @param rc - routing context to handle
   */
  @Override
  public void handle(RoutingContext rc, ScRoutingEntry scRoutingEntry) {
    var rq = rc.request();
    log.info("Handling ingress request [method: {}, uri: {}, requestId: {}]",
      rq::method, dumpUri(rc), () -> rq.getHeader(REQUEST_ID));
    
    requestFilterService.filterIngressRequest(rc)
      .onSuccess(authResponse -> forwardRequest(rc))
      .onFailure(error -> errorHandler.sendErrorResponse(rc, error));
  }

  private void forwardRequest(RoutingContext rc) {
    var request = rc.request();

    var headers = request.headers();
    headers.set(OkapiHeaders.URL, sidecarProperties.getUrl());
    rc.put("uct", System.currentTimeMillis());

    var path = pathProcessor.getModulePath(rc.request().path());
    log.info("Forwarding ingress request to underlying module: [method: {}, uri: {}]", request::method, dumpUri(rc));

    var absUri = moduleProperties.getUrl() + path;
    requestForwardingService.forwardIngress(rc, absUri);
  }
}
