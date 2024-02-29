package org.folio.sidecar.service.filter;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.service.SidecarSignatureService;

@ApplicationScoped
@RequiredArgsConstructor
public class SidecarSignatureFilter implements IngressRequestFilter {

  private final SidecarSignatureService sidecarSignatureService;

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    return Future.succeededFuture(routingContext)
      .map(sidecarSignatureService::populateSignature);
  }

  @Override
  public int getOrder() {
    return 170;
  }
}
