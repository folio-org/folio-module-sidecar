package org.folio.sidecar.exception;

import lombok.Getter;

@Getter
public class KeycloakUnhandledAuthorizationException extends RuntimeException {

  private final int statusCode;

  public KeycloakUnhandledAuthorizationException(int statusCode) {
    super("Authorization service error");
    this.statusCode = statusCode;
  }
}
