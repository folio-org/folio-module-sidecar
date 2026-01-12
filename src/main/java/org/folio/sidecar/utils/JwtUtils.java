package org.folio.sidecar.utils;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.endsWithIgnoreCase;
import static org.folio.sidecar.utils.TokenUtils.tokenHash;

import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import org.eclipse.microprofile.jwt.JsonWebToken;

@UtilityClass
public class JwtUtils {

  public static final String SESSION_ID_CLAIM = "sid";
  public static final String USER_ID_CLAIM = "user_id";

  /**
   * Extracts the origin tenant the token was issued.
   *
   * @param token {@link JsonWebToken} jwt token
   * @return the tenant id
   */
  public static String getOriginTenant(JsonWebToken token) {
    var issuer = token.getIssuer();
    return issuer.substring(issuer.lastIndexOf('/') + 1);
  }

  public static String getSessionIdClaim(JsonWebToken token) {
    return token.getClaim(SESSION_ID_CLAIM);
  }

  public static Optional<String> getUserIdClaim(JsonWebToken token) {
    return ofNullable(token.getClaim(USER_ID_CLAIM));
  }

  public static Long getTokenExpirationTime(JsonWebToken token) {
    return token.getExpirationTime();
  }

  public static String trimTokenBearer(String token) {
    return token == null || !token.startsWith("Bearer ") ? token : token.substring(7).trim();
  }

  public static String dumpTokenClaims(JsonWebToken token) {
    return token.getClaimNames().stream()
      .map(claimToString(token))
      .reduce((a, b) -> a + "\n" + b)
      .orElse("");
  }

  private static Function<String, String> claimToString(JsonWebToken token) {
    return claimName -> {
      var value = endsWithIgnoreCase(claimName, "token")
        ? tokenHash(token.getClaim(claimName))
        : token.getClaim(claimName);

      return format("%s = %s", claimName, value);
    };
  }
}
