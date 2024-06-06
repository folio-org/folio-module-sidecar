package org.folio.sidecar.configuration.properties;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Data
@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor
public class SidecarProperties {

  @ConfigProperty(name = "sidecar.name") String name;
  @ConfigProperty(name = "sidecar.url") String url;
  @ConfigProperty(name = "sidecar.module-path-prefix.enabled") boolean modulePrefixEnabled;
  @ConfigProperty(name = "sidecar.forward-unknown-requests") boolean forwardUnknownRequests;
  @ConfigProperty(name = "sidecar.forward-unknown-requests.destination") String unknownRequestsDestination;
  @ConfigProperty(name = "sidecar.cross-tenant.enabled") boolean crossTenantEnabled;

  @ConfigProperty(name = "sidecar.client.tls.enabled", defaultValue = "false") boolean clientTlsEnabled;
  // replace String with Optional<String> for the next 4 properties
  // to support missing property values instead of using " " as a default value
  // see also https://quarkus.io/guides/config-reference#inject
  @ConfigProperty(name = "sidecar.client.tls.trust-store-path", defaultValue = " ") String trustStorePath;
  @ConfigProperty(name = "sidecar.client.tls.trust-store-password", defaultValue = " ") String trustStorePassword;
  @ConfigProperty(name = "sidecar.client.tls.trust-store-file-type", defaultValue = " ") String trustStoreFileType;
  @ConfigProperty(name = "sidecar.client.tls.trust-store-provider", defaultValue = " ") String trustStoreProvider;
}
