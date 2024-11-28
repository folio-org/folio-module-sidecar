package org.folio.sidecar.service.token;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import lombok.experimental.UtilityClass;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;

@UtilityClass
public class TokenUtils {

  public static String tokenHash(String token) {
    return isNotEmpty(token) ? DigestUtils.sha256Hex(token) : null;
  }

  public static String tokenResponseAsString(TokenResponse tokenResponse) {
    return new ToStringBuilder(tokenResponse)
      .append("accessToken", tokenHash(tokenResponse.getAccessToken()))
      .append("refreshToken", tokenHash(tokenResponse.getRefreshToken()))
      .append("expiresIn", tokenResponse.getExpiresIn())
      .toString();
  }
}
