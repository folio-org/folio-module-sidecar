package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.filter.IngressFilterOrder.REQUEST_VALIDATION;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class RequestValidationFilter implements IngressRequestFilter {

  private static final String CONTENT_LENGTH_HEADER = "Content-Length";

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    if (HttpMethod.GET.equals(rc.request().method()) && isContentLengthByHeaderExist(rc.request().headers())) {
      return failedFuture(new BadRequestException("GET requests should not have a body"));
    }
    return succeededFuture(rc);
  }

  @Override
  public int getOrder() {
    return REQUEST_VALIDATION.getOrder();
  }

  private boolean isContentLengthByHeaderExist(MultiMap headers) {
    if (headers.contains(CONTENT_LENGTH_HEADER)) {
      var length = headers.get(CONTENT_LENGTH_HEADER);
      return Integer.parseInt(length) > 0;
    }
    return false;
  }
}
