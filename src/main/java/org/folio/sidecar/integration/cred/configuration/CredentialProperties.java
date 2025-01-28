package org.folio.sidecar.integration.cred.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "credentials")
public interface CredentialProperties {

  Client client();

  User user();

  interface Client {

    CacheSettings cache();
  }

  interface User {

    CacheSettings cache();
  }

  interface CacheSettings {

    @WithDefault("5")
    int initialCapacity();

    @WithDefault("50")
    int maxSize();
  }
}
