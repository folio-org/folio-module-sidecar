package org.folio.sidecar.integration.am.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class ModuleBootstrap {

  private ModuleBootstrapDiscovery module;
  private List<ModuleBootstrapDiscovery> requiredModules = new ArrayList<>();
}
