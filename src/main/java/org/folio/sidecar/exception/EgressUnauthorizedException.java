package org.folio.sidecar.exception;

/**
 * Thrown when an upstream module returns {@code 401 Unauthorized} on a module-to-module (egress) request.
 */
public class EgressUnauthorizedException extends RuntimeException {

  /**
   * Constructs a new exception with the given detail message.
   *
   * @param message detail message describing the failing egress request
   */
  public EgressUnauthorizedException(String message) {
    super(message);
  }
}
