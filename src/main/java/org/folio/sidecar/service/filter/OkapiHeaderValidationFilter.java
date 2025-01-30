package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class OkapiHeaderValidationFilter implements IngressRequestFilter {

  private final OkapiHeaderValidationService headerValidationService;

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    headerValidationService.validateHeaders(routingContext.request());
    return succeededFuture(routingContext);
  }

  @Override
  public int getOrder() {
    return IngressFilterOrder.HEADER_VALIDATION.getOrder();
  }
}