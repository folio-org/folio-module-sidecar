package org.folio.sidecar.service.filter;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.service.SidecarSignatureService;

@RequiredArgsConstructor
@ApplicationScoped
public class SidecarSignatureRemover implements EgressRequestFilter {

  private final SidecarSignatureService sidecarSignatureService;

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    sidecarSignatureService.removeSignature(routingContext);
    return Future.succeededFuture(routingContext);
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
