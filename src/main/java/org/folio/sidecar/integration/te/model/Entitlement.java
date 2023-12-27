package org.folio.sidecar.integration.te.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Tenant entitlement descriptor.
 */
@Data
@AllArgsConstructor(staticName = "of")
@RegisterForReflection
public class Entitlement {

  /**
   * An application identifier.
   */
  @JsonProperty("applicationId")
  private String applicationId;

  /**
   * A tenant identifier.
   */
  @JsonProperty("tenantId")
  private String tenantId;
}

