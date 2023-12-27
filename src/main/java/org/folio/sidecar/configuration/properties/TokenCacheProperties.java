package org.folio.sidecar.configuration.properties;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Data
@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor
public class TokenCacheProperties {

  @ConfigProperty(name = "token-cache.capacity.initial") Integer initialCapacity;
  @ConfigProperty(name = "token-cache.capacity.max") Integer maxCapacity;
  /**
   * Specifies the amount of seconds for a cache entry invalidation prior to the token expiration.
   * The purpose of early cache entry expiration is to minimize a risk that a token expires
   * when a request is being processed.
   */
  @ConfigProperty(name = "token-cache.refresh-before-expiry-sec") Integer refreshBeforeExpirySeconds;
}
