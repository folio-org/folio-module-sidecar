package org.folio.sidecar.integration.users.configuration;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.Produces;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.users.configuration.property.ModUsersProperties;
import org.folio.sidecar.integration.users.model.User;

@Log4j2
@Dependent
public class UserCacheConfiguration {

  @Produces
  @ApplicationScoped
  public Cache<String, User> userCache(ModUsersProperties properties) {
    return Caffeine.newBuilder()
      .expireAfterWrite(properties.getCacheExpirationSeconds(), SECONDS)
      .initialCapacity(properties.getCacheInitialCapacity())
      .maximumSize(properties.getCacheMaxCapacity())
      .removalListener((k, jwt, cause) -> log.debug("Cached user removed: key={}, cause={}", k, cause))
      .build();
  }
}
