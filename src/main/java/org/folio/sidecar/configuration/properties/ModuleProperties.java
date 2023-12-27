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
public class ModuleProperties {

  @ConfigProperty(name = "module.id") String id;
  @ConfigProperty(name = "module.name") String name;
  @ConfigProperty(name = "module.version") String version;
  @ConfigProperty(name = "module.url") String url;
  @ConfigProperty(name = "module.health-path") String healthPath;
}
