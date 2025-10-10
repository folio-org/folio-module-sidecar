package org.folio.sidecar.integration.keycloak.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.jwt.openid.JsonWebTokenParser;
import org.folio.jwt.openid.OpenidJwtParserProvider;
import org.folio.jwt.openid.configuration.JwtParserConfiguration;
import org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse;

@Log4j2
@Dependent
public class KeycloakSidecarConfiguration {

  @ApplicationScoped
  public Cache<String, JsonWebToken> kcAuthorizationCache(KeycloakProperties props, JsonWebTokenExpiry expiry) {
    return Caffeine.newBuilder()
      .expireAfter(expiry)
      .initialCapacity(10)
      .maximumSize(props.getAuthorizationCacheMaxSize())
      .removalListener((k, jwt, cause) -> log.debug("Cached access token removed: key={}, cause={}", k, cause))
      .build();
  }

  @ApplicationScoped
  public Cache<String, TokenIntrospectionResponse> tokenIntrospectionCache(KeycloakProperties props,
    TokenIntrospectionExpiry expiry) {
    return Caffeine.newBuilder()
      .initialCapacity(10)
      .maximumSize(props.getTokenIntrospectionCacheMaxSize())
      .removalListener((k, res, cause) -> log.debug("Cached token introspection removed: key={}, cause={}", k, cause))
      .expireAfter(expiry)
      .build();
  }

  @ApplicationScoped
  public JsonWebTokenParser jsonWebTokenParser(KeycloakProperties keycloakProperties,
    OpenidJwtParserProvider openidJwtParserProvider, ObjectMapper objectMapper) {
    var jwtParserConfiguration = JwtParserConfiguration.builder()
      .issuerRootUri(keycloakProperties.getUrl())
      .validateUri(keycloakProperties.isUriValidationEnabled())
      .build();

    return new JsonWebTokenParser(objectMapper, jwtParserConfiguration, openidJwtParserProvider);
  }

  @ApplicationScoped
  public OpenidJwtParserProvider openidJwtParserProvider(KeycloakProperties keycloakProperties) {
    return new OpenidJwtParserProvider(
      keycloakProperties.getJwksRefreshInterval(),
      keycloakProperties.getForcedJwksRefreshInterval(),
      keycloakProperties.getJwksBaseUrl());
  }
}
