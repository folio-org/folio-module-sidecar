package org.folio.sidecar.integration.okapi.filter;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.service.filter.IngressRequestFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@ApplicationScoped
public class OkapiHeaderValidationFilter implements IngressRequestFilter {

  private static final int FILTER_ORDER = 110;

  @Override
  public void filter(RoutingContext routingContext) {
    MultiMap headers = routingContext.request().headers();
    Map<String, List<String>> duplicateOkapiHeaders = findDuplicateOkapiHeaders(headers);

    if (!duplicateOkapiHeaders.isEmpty()) {
      String duplicateHeadersMessage = formatDuplicateHeadersMessage(duplicateOkapiHeaders);
      log.debug("Rejecting request due to duplicate X-Okapi-* headers: {}", duplicateHeadersMessage);
      routingContext.response()
        .setStatusCode(400)
        .putHeader("Content-Type", "text/plain")
        .end("Request contains duplicate X-Okapi-* headers: " + duplicateHeadersMessage);
      return;
    }

    routingContext.next();
  }

  @Override
  public int getOrder() {
    return FILTER_ORDER;
  }

  private Map<String, List<String>> findDuplicateOkapiHeaders(MultiMap headers) {
    return headers.entries().stream()
      .filter(entry -> entry.getKey().toLowerCase().startsWith(OkapiHeaders.PREFIX.toLowerCase()))
      .collect(Collectors.groupingBy(Map.Entry::getKey))
      .entrySet().stream()
      .filter(entry -> entry.getValue().size() > 1)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String formatDuplicateHeadersMessage(Map<String, List<String>> duplicateHeaders) {
    return duplicateHeaders.entrySet().stream()
      .map(entry -> String.format("%s=%s", entry.getKey(), 
        entry.getValue().stream()
          .map(header -> header.getValue())
          .collect(Collectors.joining(", "))))
      .collect(Collectors.joining("; "));
  }
}