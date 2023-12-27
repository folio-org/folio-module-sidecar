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
}
