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

  private static final long EXPIRE_OFFSET = 500L;

  private final long inactiveTokenTtl;

  @Inject
  public TokenIntrospectionExpiry(KeycloakProperties keycloakProperties) {
    this.inactiveTokenTtl = keycloakProperties.getInactiveTokenIntrospectionTtl();
  }

  @Override
  public long expireAfterCreate(String key, TokenIntrospectionResponse value, long currentTime) {
    if (value.getExpirationTime() == null) {
      return SECONDS.toNanos(inactiveTokenTtl);
    }
    return SECONDS.toNanos(value.getExpirationTime()) - MILLISECONDS.toNanos(currentTimeMillis()) - EXPIRE_OFFSET;
  }

  @Override
  public long expireAfterUpdate(String key, TokenIntrospectionResponse value, long currentTime, long currentDuration) {
    if (value.getExpirationTime() == null) {
      return SECONDS.toNanos(inactiveTokenTtl);
    }
    return SECONDS.toNanos(value.getExpirationTime()) - MILLISECONDS.toNanos(currentTimeMillis()) - EXPIRE_OFFSET;
  }

  @Override
  public long expireAfterRead(String key, TokenIntrospectionResponse value, long currentTime, long currentDuration) {
    return currentDuration;
  }
}
