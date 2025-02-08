package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.filter.IngressFilterOrder.HEADER_VALIDATION;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.service.header.OkapiHeaderValidator;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class OkapiHeaderValidationFilter implements IngressRequestFilter {

  private final OkapiHeaderValidator headerValidator;

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    try {
      headerValidator.validateOkapiHeaders(rc.request().headers());
      return succeededFuture(rc);
    } catch (Exception e) {
      return failedFuture(e);
    }
  }

  @Override
  public int getOrder() {
    return HEADER_VALIDATION.getOrder();
  }
}
