package org.folio.sidecar.integration.cred.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import org.folio.sidecar.configuration.properties.CacheSettings;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;

public class CredentialConfiguration {

  @ApplicationScoped
  @SuppressWarnings("unchecked")
  public Cache<String, ClientCredentials> clientCredentialsCache(CredentialProperties props) {
    return (Cache<String, ClientCredentials>) fromSettings(props.client().cache());
  }

  @ApplicationScoped
  @SuppressWarnings("unchecked")
  public Cache<String, UserCredentials> userCredentialsCache(CredentialProperties props) {
    return (Cache<String, UserCredentials>) fromSettings(props.user().cache());
  }

  private static Cache<?, ?> fromSettings(CacheSettings props) {
    var builder = Caffeine.newBuilder();

    props.initialCapacity().ifPresent(builder::initialCapacity);
    props.maxSize().ifPresent(builder::maximumSize);

    return builder.build();
  }
}
