package org.folio.sidecar.integration.te.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class EntitlementList {

  @JsonProperty("entitlements")
  private List<Entitlement> entitlements;

  @JsonProperty("totalRecords")
  private Integer totalRecords;
}
