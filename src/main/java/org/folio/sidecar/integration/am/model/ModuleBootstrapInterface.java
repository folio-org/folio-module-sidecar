package org.folio.sidecar.integration.am.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class ModuleBootstrapInterface {

  /**
   * Interface identifier.
   */
  private String id;
  /**
   * Interface version in major.minor format.
   */
  private String version;
  /**
   * Interface type.
   */
  private String interfaceType;
  /**
   * Endpoint entries for this interface.
   */
  private List<ModuleBootstrapEndpoint> endpoints = new ArrayList<>();
}
