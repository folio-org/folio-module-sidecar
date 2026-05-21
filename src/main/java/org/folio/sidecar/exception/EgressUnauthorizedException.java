package org.folio.sidecar.exception;

public class EgressUnauthorizedException extends RuntimeException {

  public EgressUnauthorizedException(String message) {
    super(message);
  }
}
