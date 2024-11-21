package org.folio.sidecar.integration.keycloak.configuration;

import com.github.benmanes.caffeine.cache.Expiry;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;

@Log4j2
@RequiredArgsConstructor
public class TokenCacheExpiry implements Expiry<String, TokenResponse> {

  private final Function<TokenResponse, Long> expireAfterCreateFunc;

  @Override
  public long expireAfterCreate(String tenant, TokenResponse token, long currentTime) {
    log.debug("expireAfterCreate called: tenant = {}, tokenExpiresIn = {}", tenant, token.getExpiresIn());
    Long expiresAfter = expireAfterCreateFunc.apply(token);
    log.debug("expireAfterCreate result: expiresAfter = {} nanos", expiresAfter);
    return expiresAfter;
  }

  @Override
  public long expireAfterUpdate(String tenant, TokenResponse token, long currentTime, long currentDuration) {
    log.debug("expireAfterUpdate called: tenant = {}, tokenExpiresIn = {}", tenant, token.getExpiresIn());
    log.debug("expireAfterUpdate result: expiresAfter = {} nanos", currentDuration);
    return currentDuration;
  }

  @Override
  public long expireAfterRead(String tenant, TokenResponse token, long currentTime, long currentDuration) {
    log.debug("expireAfterRead called: tenant = {}, tokenExpiresIn = {}", tenant, token.getExpiresIn());
    log.debug("expireAfterRead result: expiresAfter = {} nanos", currentDuration);
    return currentDuration;
  }
}
