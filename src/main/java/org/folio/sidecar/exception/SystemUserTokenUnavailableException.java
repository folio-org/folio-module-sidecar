package org.folio.sidecar.exception;

/**
 * Thrown when a required system-user token cannot be obtained for an egress request.
 */
public class SystemUserTokenUnavailableException extends RuntimeException {

  public SystemUserTokenUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
