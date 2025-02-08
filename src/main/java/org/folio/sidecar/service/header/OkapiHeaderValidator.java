package org.folio.sidecar.service.header;

import io.vertx.core.MultiMap;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.exception.DuplicateHeaderException;
import org.folio.sidecar.integration.okapi.OkapiHeaders;

import java.util.List;

@Log4j2
@ApplicationScoped
public class OkapiHeaderValidator {

  /**
   * Validates that there are no duplicate Okapi headers in the request.
   *
   * @param headers - HTTP headers to validate
   * @throws DuplicateHeaderException if any Okapi header is duplicated
   */
  public void validateOkapiHeaders(MultiMap headers) {
    headers.names().stream()
      .filter(name -> name.toLowerCase().startsWith("x-okapi-"))
      .forEach(headerName -> checkDuplicateHeaders(headers, headerName));
  }

  private void checkDuplicateHeaders(MultiMap headers, String headerName) {
    List<String> headerValues = headers.getAll(headerName);
    if (headerValues.size() > 1) {
      log.debug("Found duplicate header: {} with values: {}", headerName, headerValues);
      throw new DuplicateHeaderException(headerName);
    }
  }
}