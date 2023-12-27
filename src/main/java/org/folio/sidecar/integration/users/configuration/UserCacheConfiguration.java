package org.folio.sidecar.integration.users.configuration;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.Produces;
import org.folio.sidecar.integration.users.configuration.property.ModUsersProperties;
import org.folio.sidecar.integration.users.model.User;

@Dependent
public class UserCacheConfiguration {

  @Produces
  @ApplicationScoped
  public Cache<String, User> userCache(ModUsersProperties properties) {
    return Caffeine.newBuilder()
      .expireAfterWrite(properties.getCacheExpirationSeconds(), SECONDS)
      .initialCapacity(properties.getCacheInitialCapacity())
      .maximumSize(properties.getCacheMaxCapacity())
      .build();
  }
}
