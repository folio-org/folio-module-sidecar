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
public class GatewayProperties {

  @ConfigProperty(name = "gateway.client.tls.enabled") boolean clientTlsEnabled;
  @ConfigProperty(name = "gateway.client.tls.trust-store-path") String trustStorePath;
  @ConfigProperty(name = "gateway.client.tls.trust-store-password") String trustStorePassword;
  @ConfigProperty(name = "gateway.client.tls.trust-store-file-type") String trustStoreFileType;
  @ConfigProperty(name = "gateway.client.tls.trust-store-provider") String trustStoreProvider;
}
