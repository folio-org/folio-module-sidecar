package org.folio.sidecar.integration.keycloak.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Data
@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakProperties {

  @ConfigProperty(name = "keycloak.url") String url;
  @ConfigProperty(name = "keycloak.login.client-suffix") String loginClientSuffix;
  @ConfigProperty(name = "keycloak.admin.client-id") String adminClientId;
  @ConfigProperty(name = "keycloak.service.client-id") String serviceClientId;
  @ConfigProperty(name = "keycloak.impersonation-client") String impersonationClient;

  @ConfigProperty(name = "keycloak.authorization-cache-max-size") long authorizationCacheMaxSize;
  @ConfigProperty(name = "keycloak.authorization-cache-ttl-offset") long authorizationCacheTtlOffset;

  @ConfigProperty(name = "keycloak.client.tls.enabled") boolean clientTlsEnabled;
  @ConfigProperty(name = "keycloak.client.tls.trust-store-path") String trustStorePath;
  @ConfigProperty(name = "keycloak.client.tls.trust-store-password") String trustStorePassword;
  @ConfigProperty(name = "keycloak.client.tls.trust-store-file-type") String trustStoreFileType;
  @ConfigProperty(name = "keycloak.client.tls.trust-store-provider") String trustStoreProvider;
}
