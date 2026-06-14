package org.folio.sidecar.integration.am.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class ModuleBootstrapResponse {

  private ModuleBootstrap ingress;
  private EgressBootstrapResult egress;
}
