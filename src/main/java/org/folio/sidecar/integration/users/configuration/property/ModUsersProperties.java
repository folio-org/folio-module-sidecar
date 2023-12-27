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

  @ConfigProperty(name = "mod-users.url") String url;
  @ConfigProperty(name = "mod-users.cache-expiration-seconds") int cacheExpirationSeconds;
  @ConfigProperty(name = "mod-users.cache-initial-capacity") int cacheInitialCapacity;
  @ConfigProperty(name = "mod-users.cache-max-capacity") int cacheMaxCapacity;
}
