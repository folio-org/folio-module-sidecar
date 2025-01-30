package org.folio.sidecar.integration.cred.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;

public class CredentialConfiguration {

  @Produces
  @ApplicationScoped
  @SuppressWarnings("unchecked")
  public Cache<String, ClientCredentials> clientCredentialsCache(CredentialProperties props) {
    return (Cache<String, ClientCredentials>) fromSettings(props.client().cache());
  }

  @Produces
  @ApplicationScoped
  @SuppressWarnings("unchecked")
  public Cache<String, UserCredentials> userCredentialsCache(CredentialProperties props) {
    return (Cache<String, UserCredentials>) fromSettings(props.user().cache());
  }

  private static Cache<?, ?> fromSettings(CredentialProperties.CacheSettings props) {
    return Caffeine.newBuilder()
      .initialCapacity(props.initialCapacity())
      .maximumSize(props.maxSize())
      .build();
  }
}
