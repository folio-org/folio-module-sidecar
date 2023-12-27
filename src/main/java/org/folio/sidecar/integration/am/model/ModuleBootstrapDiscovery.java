package org.folio.sidecar.integration.am.model;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private List<ModuleBootstrapInterface> interfaces = new ArrayList<>();

  /**
   * Get Map of interface id as key and list of RoutingEntries as value.
   */
  @JsonIgnore
  public Map<String, List<ModuleBootstrapEndpoint>> getProvidedRoutingEntriesMap() {
    return interfaces.stream()
      .collect(toMap(ModuleBootstrapInterface::getId, ModuleBootstrapInterface::getEndpoints));
  }
}
