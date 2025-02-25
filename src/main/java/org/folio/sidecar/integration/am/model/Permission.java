package org.folio.sidecar.integration.am.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class Permission {

  /**
   * Permission name.
   */
  @JsonProperty
  private String permissionName;
}
