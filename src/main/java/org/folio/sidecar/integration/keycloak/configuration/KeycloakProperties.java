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
  @ConfigProperty(name = "keycloak.uri-validation.enabled") boolean uriValidationEnabled;
  @ConfigProperty(name = "keycloak.login.client-suffix") String loginClientSuffix;
  @ConfigProperty(name = "keycloak.admin.client-id") String adminClientId;
  @ConfigProperty(name = "keycloak.service.client-id") String serviceClientId;
  @ConfigProperty(name = "keycloak.impersonation-client") String impersonationClient;

  @ConfigProperty(name = "keycloak.authorization-cache-max-size") long authorizationCacheMaxSize;
  @ConfigProperty(name = "keycloak.authorization-cache-ttl-offset") long authorizationCacheTtlOffset;

  @ConfigProperty(name = "keycloak.client.tls.enabled", defaultValue = "false") boolean clientTlsEnabled;
  // replace String with Optional<String> for the next 4 properties
  // to support missing property values instead of using " " as a default value
  // see also https://quarkus.io/guides/config-reference#inject
  @ConfigProperty(name = "keycloak.client.tls.trust-store-path", defaultValue = " ") String trustStorePath;
  @ConfigProperty(name = "keycloak.client.tls.trust-store-password", defaultValue = " ") String trustStorePassword;
  @ConfigProperty(name = "keycloak.client.tls.trust-store-file-type", defaultValue = " ") String trustStoreFileType;
  @ConfigProperty(name = "keycloak.client.tls.trust-store-provider", defaultValue = " ") String trustStoreProvider;
}
