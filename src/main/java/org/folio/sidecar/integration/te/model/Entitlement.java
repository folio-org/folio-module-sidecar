package org.folio.sidecar.integration.te.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant entitlement descriptor.
 */
@Data
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
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

  @JsonProperty("modules")
  private List<String> modules = new ArrayList<>();
}

