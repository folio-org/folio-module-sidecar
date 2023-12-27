package org.folio.sidecar.utils;

import static java.util.Optional.ofNullable;
import static org.folio.sidecar.utils.SecurityUtils.JWT_OKAPI_USER_ID_CLAIM;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.eclipse.microprofile.jwt.JsonWebToken;

@UtilityClass
public class JwtUtils {

  public static String extractTokenIssuer(JsonWebToken token) {
    var issuer = token.getIssuer();
    return issuer.substring(issuer.lastIndexOf('/') + 1);
  }

  public static Optional<String> getUserIdClaim(JsonWebToken token) {
    return ofNullable(token.getClaim(JWT_OKAPI_USER_ID_CLAIM));
  }
}
