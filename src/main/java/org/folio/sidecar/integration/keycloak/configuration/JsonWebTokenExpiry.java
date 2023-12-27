package org.folio.sidecar.integration.keycloak.configuration;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.benmanes.caffeine.cache.Expiry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class JsonWebTokenExpiry implements Expiry<String, JsonWebToken> {

  private final long expireOffset;

  @Inject
  public JsonWebTokenExpiry(KeycloakProperties keycloakProperties) {
    this.expireOffset = MILLISECONDS.toNanos(keycloakProperties.getAuthorizationCacheTtlOffset());
  }

  @Override
  public long expireAfterCreate(String s, JsonWebToken jsonWebToken, long currentTime) {
    return SECONDS.toNanos(jsonWebToken.getExpirationTime()) - MILLISECONDS.toNanos(currentTimeMillis() + expireOffset);
  }

  @Override
  public long expireAfterUpdate(String s, JsonWebToken jsonWebToken, long currentTime, long currentDuration) {
    return currentDuration;
  }

  @Override
  public long expireAfterRead(String s, JsonWebToken jsonWebToken, long currentTime, long currentDuration) {
    return currentDuration;
  }
}
