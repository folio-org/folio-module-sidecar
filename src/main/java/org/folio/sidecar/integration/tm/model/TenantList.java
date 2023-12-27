package org.folio.sidecar.integration.tm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class TenantList {

  @JsonProperty("tenants")
  private List<Tenant> tenants;

  @JsonProperty("totalRecords")
  private Integer totalRecords;
}
