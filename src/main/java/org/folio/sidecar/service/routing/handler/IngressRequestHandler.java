package org.folio.sidecar.service.routing.handler;

import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.filter.RequestFilterService;

@Log4j2
@Named
@ApplicationScoped
@RequiredArgsConstructor
class IngressRequestHandler implements RoutingEntryHandler {

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
  public Future<Void> handle(ScRoutingEntry scRoutingEntry, RoutingContext rc) {
    var rq = rc.request();
    log.debug("Handling ingress request [method: {}, uri: {}, requestId: {}]",
      rq::method, dumpUri(rc), () -> rq.getHeader(REQUEST_ID));
    
    return requestFilterService.filterIngressRequest(rc)
      .compose(authResponse -> forwardRequest(rc));
  }

  private Future<Void> forwardRequest(RoutingContext rc) {
    var request = rc.request();

    var headers = request.headers();
    headers.set(OkapiHeaders.URL, sidecarProperties.getUrl());
    rc.put("uct", System.currentTimeMillis());

    var path = pathProcessor.getModulePath(rc.request().path());
    log.debug("Forwarding ingress request to underlying module: [method: {}, uri: {}]", request::method, dumpUri(rc));

    var absUri = moduleProperties.getUrl() + path;

    return requestForwardingService.forwardIngress(rc, absUri);
  }
}
