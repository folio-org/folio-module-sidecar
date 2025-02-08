package org.folio.sidecar.exception;

public class DuplicateHeaderException extends RuntimeException {

  public DuplicateHeaderException(String headerName) {
    super("Duplicate header found: " + headerName);
  }
}