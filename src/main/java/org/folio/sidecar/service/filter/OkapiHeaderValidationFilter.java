package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class OkapiHeaderValidationFilter implements IngressRequestFilter {

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    try {
      validateOkapiHeaders(routingContext.request().headers());
      return succeededFuture(routingContext);
    } catch (IllegalArgumentException e) {
      return failedFuture(e);
    }
  }

  private void validateOkapiHeaders(MultiMap headers) {
    Map<String, List<String>> duplicateHeaders = new HashMap<>();

    headers.entries().stream()
      .filter(entry -> entry.getKey().toLowerCase().startsWith(OkapiHeaders.PREFIX.toLowerCase()))
      .forEach(entry -> {
        String headerName = entry.getKey();
        List<String> values = headers.getAll(headerName);
        if (values.size() > 1) {
          duplicateHeaders.put(headerName, values);
        }
      });

    if (!duplicateHeaders.isEmpty()) {
      String errorMessage = String.format("Request contains duplicate Okapi headers: %s", duplicateHeaders);
      log.debug(errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }
  }

  @Override
  public int getOrder() {
    return 0;  // Execute this filter first
  }
}