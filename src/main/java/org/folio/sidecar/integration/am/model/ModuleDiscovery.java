package org.folio.sidecar.integration.am.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class ModuleDiscovery {

  @JsonProperty(required = true)
  private String location;

  @JsonProperty
  private String id;

  @JsonProperty(required = true)
  private String name;

  @JsonProperty(required = true)
  private String version;

  public ModuleDiscovery location(String location) {
    this.location = location;
    return this;
  }

  public ModuleDiscovery id(String id) {
    this.id = id;
    return this;
  }

  public ModuleDiscovery name(String name) {
    this.name = name;
    return this;
  }

  public ModuleDiscovery version(String version) {
    this.version = version;
    return this;
  }
}
