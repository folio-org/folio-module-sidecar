package org.folio.sidecar.integration.am.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;
import lombok.Data;

@Data
@RegisterForReflection
public class ModuleBootstrapResponse {

  private ModuleBootstrap ingress;
  private Map<String, EgressBootstrapResult> egress;
}
