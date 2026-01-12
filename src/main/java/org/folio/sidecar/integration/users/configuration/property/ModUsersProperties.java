package org.folio.sidecar.integration.users.configuration.property;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApplicationScoped
public class ModUsersProperties {

  @ConfigProperty(name = "mod-users-keycloak.url") String url;
  @ConfigProperty(name = "mod-users-keycloak.cache-expiration-seconds") int cacheExpirationSeconds;
  @ConfigProperty(name = "mod-users-keycloak.cache-initial-capacity") int cacheInitialCapacity;
  @ConfigProperty(name = "mod-users-keycloak.cache-max-capacity") int cacheMaxCapacity;
  @ConfigProperty(name = "mod-users-keycloak.permission-cache-expiration-seconds",
    defaultValue = "180") int permissionCacheExpirationSeconds;
  @ConfigProperty(name = "mod-users-keycloak.permission-cache-max-capacity",
    defaultValue = "10000") int permissionCacheMaxCapacity;
}
