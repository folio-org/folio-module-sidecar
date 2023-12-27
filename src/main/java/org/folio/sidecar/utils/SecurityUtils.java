package org.folio.sidecar.utils;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.jwt.JsonWebToken;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityUtils {

  public static final String JWT_SESSION_STATE_CLAIM = "session_state";
  public static final String JWT_OKAPI_USER_ID_CLAIM = "user_id";
  public static final String JWT_ACCESS_TOKEN = "access_token";

  public static String trimTokenBearer(String token) {
    return token == null || !token.startsWith("Bearer ") ? token : token.substring(7).trim();
  }

  public static boolean isExpiredToken(JsonWebToken token) {
    var exp = token.getExpirationTime();
    return isBeforeNow(exp);
  }

  private static boolean isBeforeNow(long epochSecond) {
    var instant = Instant.ofEpochSecond(epochSecond);
    return instant.isBefore(Instant.now());
  }
}
