package org.folio.sidecar.integration.users.configuration;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.users.configuration.property.ModUsersProperties;
import org.folio.sidecar.integration.users.model.User;

@Log4j2
@Dependent
public class UserCacheConfiguration {

  @ApplicationScoped
  public Cache<String, User> userCache(ModUsersProperties properties) {
    return Caffeine.newBuilder()
      .expireAfterWrite(properties.getCacheExpirationSeconds(), SECONDS)
      .initialCapacity(properties.getCacheInitialCapacity())
      .maximumSize(properties.getCacheMaxCapacity())
      .removalListener((k, jwt, cause) -> log.debug("Cached user removed: key={}, cause={}", k, cause))
      .build();
  }

  @ApplicationScoped
  public Cache<String, List<String>> userParmissionCache(ModUsersProperties properties) {
    return Caffeine.newBuilder()
      .expireAfterWrite(properties.getCacheExpirationSeconds(), SECONDS)
      .initialCapacity(properties.getCacheInitialCapacity())
      .maximumSize(properties.getCacheMaxCapacity())
      .removalListener((k, jwt, cause) -> log.debug("Cached user permissions removed: key={}, cause={}", k, cause))
      .build();
  }
}
