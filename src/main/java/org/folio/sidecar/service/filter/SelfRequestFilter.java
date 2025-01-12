package org.folio.sidecar.service.filter;

import static org.folio.sidecar.service.filter.IngressFilterOrder.SELF_REQUEST;
import static org.folio.sidecar.utils.RoutingUtils.SELF_REQUEST_KEY;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.service.SidecarSignatureService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class SelfRequestFilter implements IngressRequestFilter {

  private final SidecarSignatureService sidecarSignatureService;

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    var selfRequest = sidecarSignatureService.isSelfRequest(rc);
    rc.put(SELF_REQUEST_KEY, selfRequest);

    if (selfRequest) {
      var rq = rc.request();
      log.info("Request is self request, skipping authorization: method = {}, uri = {}", rq::method, dumpUri(rc));
    }

    return Future.succeededFuture(rc);
  }

  @Override
  public int getOrder() {
    return SELF_REQUEST.getOrder();
  }
}
