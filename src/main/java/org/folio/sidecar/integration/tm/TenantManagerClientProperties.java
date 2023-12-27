package org.folio.sidecar.integration.tm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Data
@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class TenantManagerClientProperties {

  @ConfigProperty(name = "tm.url") String url;
  @ConfigProperty(name = "tm.batchSize") Integer batchSize;
}
