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
public class WebClientProperties {

  @ConfigProperty(name = "http.request.timeout") Long timeout;
  @ConfigProperty(name = "web.client.tls.verify.hostname") boolean tlsHostnameVerified;
  @ConfigProperty(name = "web.client.tls.port") int tlsPort;
  @ConfigProperty(name = "web.client.tls.trust-store-file") String trustStoreFilePath;
  @ConfigProperty(name = "web.client.tls.trust-store-password") String trustStorePassword;
  @ConfigProperty(name = "quarkus.http.ssl.certificate.key-store-file-type") String keyStoreFileType;
}
