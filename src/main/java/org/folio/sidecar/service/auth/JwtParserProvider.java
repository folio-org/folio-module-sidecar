package org.folio.sidecar.service.auth;

import io.smallrye.jwt.auth.principal.JWTParser;

public interface JwtParserProvider {

  /**
   * Provides {@link JWTParser} object for issuer URI.
   *
   * @param issuerUri - issuer URI as {@link String} object
   * @return {@link JWTParser} object
   */
  JWTParser getParser(String issuerUri);
}
