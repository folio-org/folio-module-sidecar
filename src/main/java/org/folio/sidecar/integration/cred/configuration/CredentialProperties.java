package org.folio.sidecar.integration.cred.configuration;

import io.smallrye.config.ConfigMapping;
import org.folio.sidecar.configuration.properties.CacheSettings;

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
}
