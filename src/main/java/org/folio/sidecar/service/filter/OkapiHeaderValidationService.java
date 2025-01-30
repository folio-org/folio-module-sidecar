package org.folio.sidecar.service.filter;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import org.folio.sidecar.exception.DuplicateHeaderException;
import org.folio.sidecar.integration.okapi.OkapiHeaders;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OkapiHeaderValidationService {

  public void validateHeaders(HttpServerRequest request) {
    var headers = request.headers();
    var duplicateHeaders = findDuplicateOkapiHeaders(headers);
    
    if (!duplicateHeaders.isEmpty()) {
      var headersList = String.join(", ", duplicateHeaders);
      throw new DuplicateHeaderException(
        String.format("Duplicate x-okapi-* headers found: [%s]. Each x-okapi-* header must appear only once.", headersList),
        duplicateHeaders
      );
    }
  }

  private List<String> findDuplicateOkapiHeaders(io.vertx.core.MultiMap headers) {
    Set<String> okapiHeaderNames = new HashSet<>();
    List<String> duplicates = new ArrayList<>();

    headers.names().stream()
      .filter(name -> name.toLowerCase().startsWith(OkapiHeaders.PREFIX.toLowerCase()))
      .forEach(name -> {
        if (!okapiHeaderNames.add(name.toLowerCase())) {
          duplicates.add(name);
        }
      });

    return duplicates;
  }
}