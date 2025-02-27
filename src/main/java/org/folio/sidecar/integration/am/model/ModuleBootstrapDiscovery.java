package org.folio.sidecar.integration.am.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class ModuleBootstrapDiscovery {

  /**
   * Module identifier.
   */
  @JsonProperty(required = true)
  private String moduleId;

  /**
   * Application identifier.
   */
  @JsonProperty(required = true)
  private String applicationId;

  /**
   * Sidecar URL.
   */
  @JsonProperty
  private String location;

  /**
   * Provided interfaces.
   */
  @JsonProperty
  private List<ModuleBootstrapInterface> interfaces = new ArrayList<>();
}
