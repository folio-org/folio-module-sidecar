package org.folio.sidecar.integration.te;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Data
@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor
public class TenantEntitlementClientProperties {

  @ConfigProperty(name = "te.url") String url;
  @ConfigProperty(name = "te.batchSize") Integer batchSize;
}
