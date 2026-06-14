package org.folio.sidecar.integration.am.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class EgressBootstrapResult {

  private boolean found;
  private ModuleBootstrap bootstrap;
}
