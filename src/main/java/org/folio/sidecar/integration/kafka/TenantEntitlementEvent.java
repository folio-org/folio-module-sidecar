package org.folio.sidecar.integration.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantEntitlementEvent {

  /**
   * A module identifier.
   */
  @JsonProperty("moduleId")
  private String moduleId;

  /**
   * A tenant name.
   */
  @JsonProperty("tenantName")
  private String tenantName;

  /**
   * A tenant identifier.
   */
  @JsonProperty("tenantId")
  private UUID tenantId;

  /**
   * An event type.
   */
  @JsonProperty("type")
  private Type type;

  /**
   * An application identifier.
   */
  @JsonProperty("applicationId")
  private String applicationId;

  public enum Type {
    ENTITLE,
    UPGRADE,
    REVOKE
  }
}
