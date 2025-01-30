package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.Error;
import org.folio.sidecar.model.error.ErrorCode;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class HeaderValidationFilter implements IngressRequestFilter {

  private static final String PREFIX = OkapiHeaders.PREFIX + "-";

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    var request = routingContext.request();
    var duplicateHeaders = findDuplicateOkapiHeaders(request);

    if (!duplicateHeaders.isEmpty()) {
      var headerNames = String.join(", ", duplicateHeaders);
      var error = new Error()
        .code(ErrorCode.VALIDATION_ERROR)
        .message("Request contains duplicate X-Okapi headers: " + headerNames);
      return failedFuture(new ValidationException(error));
    }

    return succeededFuture(routingContext);
  }

  private List<String> findDuplicateOkapiHeaders(HttpServerRequest request) {
    return request.headers().names().stream()
      .filter(this::isOkapiHeader)
      .filter(headerName -> request.headers().getAll(headerName).size() > 1)
      .collect(Collectors.toList());
  }

  private boolean isOkapiHeader(String headerName) {
    return headerName.toUpperCase().startsWith(PREFIX);
  }

  @Override
  public int getOrder() {
    return 0;
  }
}