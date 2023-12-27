package org.folio.sidecar.integration.keycloak.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Dependent
public class KeycloakCacheConfiguration {

  @Produces
  @ApplicationScoped
  public Cache<String, JsonWebToken> kcAuthorizationCache(KeycloakProperties props, JsonWebTokenExpiry expiry) {
    return Caffeine.newBuilder()
      .expireAfter(expiry)
      .initialCapacity(10)
      .maximumSize(props.getAuthorizationCacheMaxSize())
      .build();
  }
}
