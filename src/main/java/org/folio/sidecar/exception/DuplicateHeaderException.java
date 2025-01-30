package org.folio.sidecar.exception;

import jakarta.ws.rs.BadRequestException;
import java.util.List;

public class DuplicateHeaderException extends BadRequestException {

  private final List<String> duplicateHeaders;

  public DuplicateHeaderException(String message, List<String> duplicateHeaders) {
    super(message);
    this.duplicateHeaders = duplicateHeaders;
  }

  public List<String> getDuplicateHeaders() {
    return duplicateHeaders;
  }
}