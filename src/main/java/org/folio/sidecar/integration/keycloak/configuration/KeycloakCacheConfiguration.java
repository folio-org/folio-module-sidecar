package org.folio.sidecar.integration.keycloak.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Log4j2
@Dependent
public class KeycloakCacheConfiguration {

  @Produces
  @ApplicationScoped
  public Cache<String, JsonWebToken> kcAuthorizationCache(KeycloakProperties props, JsonWebTokenExpiry expiry) {
    return Caffeine.newBuilder()
      .expireAfter(expiry)
      .initialCapacity(10)
      .maximumSize(props.getAuthorizationCacheMaxSize())
      .removalListener((k, jwt, cause) -> log.debug("Cached access token removed: key={}, cause={}", k, cause))
      .build();
  }
}
