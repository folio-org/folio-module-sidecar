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
}
