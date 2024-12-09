package org.folio.sidecar.service.token;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.math.NumberUtils.max;
import static org.folio.sidecar.utils.TokenUtils.tokenResponseAsString;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.TokenCacheProperties;
import org.folio.sidecar.integration.keycloak.configuration.TokenCacheExpiry;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;

@Log4j2
@ApplicationScoped
public class AsyncTokenCacheFactory {

  private static final int MIN_EARLY_EXPIRATION_SEC = 30;

  private final TokenCacheProperties cacheProperties;

  public AsyncTokenCacheFactory(TokenCacheProperties cacheProperties) {
    this.cacheProperties = requireNonNull(cacheProperties, "Token cache properties must be provided");
    requireNonNull(cacheProperties.getInitialCapacity(), "Token cache initial capacity must be set");
    requireNonNull(cacheProperties.getMaxCapacity(), "Token cache max capacity must be set");
    requireNonNull(cacheProperties.getRefreshBeforeExpirySeconds(), "Token cache refresh before expiry must be set");
    log.debug("Token cache factory initialized: cacheProperties = {}", cacheProperties);
  }

  public AsyncLoadingCache<String, TokenResponse> createCache(CacheLoader<String, TokenResponse> cacheLoader) {
    return Caffeine.newBuilder()
      .expireAfter(new TokenCacheExpiry(this::calculateTtl))
      .scheduler(Scheduler.systemScheduler())
      .initialCapacity(cacheProperties.getInitialCapacity())
      .maximumSize(cacheProperties.getMaxCapacity())
      .evictionListener((k, jwt, cause) -> log.debug("Cached access token removed: key={}, cause={}", k, cause))
      .buildAsync(cacheLoader);
  }

  private long calculateTtl(TokenResponse token) {
    var expiresIn = token.getExpiresIn();
    var refreshBeforeExpiry = cacheProperties.getRefreshBeforeExpirySeconds();

    log.debug("Calculating token TTL: tokenExpiresIn = {} secs, refreshBeforeExpiry = {} secs",
      expiresIn, refreshBeforeExpiry);

    // invalidating a cache entry prior to the token expiration.
    var earlyExpiresIn = expiresIn - refreshBeforeExpiry;
    var duration = earlyExpiresIn > MIN_EARLY_EXPIRATION_SEC
      ? ofSeconds(earlyExpiresIn)
      : ofMillis(minusTenPercent(expiresIn * 1000));
    log.debug("Token TTL calculated: duration = {} mls, token = {}",
      duration::toMillis, () -> tokenResponseAsString(token));

    return duration.toNanos();
  }

  private long minusTenPercent(Long expiresInMillis) {
    var fraction = expiresInMillis / 10;
    var result = expiresInMillis - max(fraction, 1);
    return max(result, 1);
  }
}
