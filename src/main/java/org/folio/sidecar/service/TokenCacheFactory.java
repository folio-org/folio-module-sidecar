package org.folio.sidecar.service;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.BiConsumer;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.TokenCacheProperties;
import org.folio.sidecar.integration.keycloak.configuration.TokenCacheExpiry;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;

@Log4j2
@ApplicationScoped
public class TokenCacheFactory {

  private static final int MIN_EARLY_EXPIRATION_SEC = 30;

  private final TokenCacheProperties cacheProperties;

  public TokenCacheFactory(TokenCacheProperties cacheProperties) {
    this.cacheProperties = requireNonNull(cacheProperties, "Token cache properties must be provided");
    requireNonNull(cacheProperties.getInitialCapacity(), "Token cache initial capacity must be set");
    requireNonNull(cacheProperties.getMaxCapacity(), "Token cache max capacity must be set");
    requireNonNull(cacheProperties.getRefreshBeforeExpirySeconds(), "Token cache refresh before expiry must be set");
  }

  /**
   * Creates a token cache with the scheduler to refresh expired entries out of band of requests. The record expiration
   * is based on token TTL.
   *
   * @return a token cache.
   */
  public Cache<String, TokenResponse> createCache() {
    return createCache(null);
  }

  /**
   * Creates a token cache with a removal listener and the scheduler to refresh expired entries out of band of requests.
   * The record expiration is based on token TTL.
   *
   * @param refreshFunction Specifies a refresh function that is called when a cache entry is removed on expiration.
   *   Ignored if null.
   * @return a token cache.
   */
  public Cache<String, TokenResponse> createCache(BiConsumer<String, TokenResponse> refreshFunction) {
    var builder = Caffeine.newBuilder()
      .expireAfter(new TokenCacheExpiry(this::calculateTtl))
      .scheduler(Scheduler.systemScheduler())
      .initialCapacity(cacheProperties.getInitialCapacity())
      .maximumSize(cacheProperties.getMaxCapacity())
      .removalListener((k, jwt, cause) -> log.debug("Cached token removed: key={}, cause={}", k, cause));

    if (refreshFunction != null) {
      builder.removalListener(refreshOnExpiration(refreshFunction));
    }
    return builder.build();
  }

  private long calculateTtl(TokenResponse token) {
    var expiresIn = token.getExpiresIn();
    var refreshBeforeExpiry = cacheProperties.getRefreshBeforeExpirySeconds();
    // invalidating a cache entry prior to the token expiration.
    var earlyExpiresIn = expiresIn - refreshBeforeExpiry;
    var duration = earlyExpiresIn > MIN_EARLY_EXPIRATION_SEC ? ofSeconds(earlyExpiresIn) : ofSeconds(expiresIn);
    return duration.toNanos();
  }

  private static RemovalListener<String, TokenResponse> refreshOnExpiration(
    BiConsumer<String, TokenResponse> refreshFunction) {
    return (tenant, token, cause) -> {
      if (cause == RemovalCause.EXPIRED) {
        refreshFunction.accept(tenant, token);
      }
    };
  }
}
