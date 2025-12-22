package org.folio.sidecar.integration.keycloak.configuration;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.benmanes.caffeine.cache.Expiry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TokenIntrospectionExpiry implements Expiry<String, TokenIntrospectionResponse> {

  private final long inactiveTokenTtl;
  private final long expireOffsetNanos;

  @Inject
  public TokenIntrospectionExpiry(KeycloakProperties keycloakProperties) {
    this.inactiveTokenTtl = keycloakProperties.getInactiveTokenIntrospectionTtl();
    this.expireOffsetNanos = MILLISECONDS.toNanos(keycloakProperties.getIntrospectionCacheTtlOffset());
  }

  @Override
  public long expireAfterCreate(String key, TokenIntrospectionResponse value, long currentTime) {
    if (value.getExpirationTime() == null) {
      return SECONDS.toNanos(inactiveTokenTtl);
    }
    var expiresIn = SECONDS.toNanos(value.getExpirationTime()) - MILLISECONDS.toNanos(currentTimeMillis())
      - expireOffsetNanos;
    return Math.max(expiresIn, 0);
  }

  @Override
  public long expireAfterUpdate(String key, TokenIntrospectionResponse value, long currentTime, long currentDuration) {
    if (value.getExpirationTime() == null) {
      return SECONDS.toNanos(inactiveTokenTtl);
    }
    var expiresIn = SECONDS.toNanos(value.getExpirationTime()) - MILLISECONDS.toNanos(currentTimeMillis())
      - expireOffsetNanos;
    return Math.max(expiresIn, 0);
  }

  @Override
  public long expireAfterRead(String key, TokenIntrospectionResponse value, long currentTime, long currentDuration) {
    return currentDuration;
  }
}
