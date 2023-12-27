package org.folio.sidecar.integration.keycloak.configuration;

import com.github.benmanes.caffeine.cache.Expiry;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;

@RequiredArgsConstructor
public class TokenCacheExpiry implements Expiry<String, TokenResponse> {

  private final Function<TokenResponse, Long> expireAfterCreateFunc;

  @Override
  public long expireAfterCreate(String tenant, TokenResponse token, long currentTime) {
    return expireAfterCreateFunc.apply(token);
  }

  @Override
  public long expireAfterUpdate(String tenant, TokenResponse token, long currentTime, long currentDuration) {
    return currentDuration;
  }

  @Override
  public long expireAfterRead(String tenant, TokenResponse token, long currentTime, long currentDuration) {
    return currentDuration;
  }
}
