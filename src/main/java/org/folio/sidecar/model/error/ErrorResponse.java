package org.folio.sidecar.model.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class ErrorResponse {

  @JsonProperty("errors")
  private List<Error> errors = null;

  @JsonProperty("total_records")
  private Integer totalRecords;

  public ErrorResponse errors(List<Error> errors) {
    this.errors = errors;
    return this;
  }

  public ErrorResponse totalRecords(Integer totalRecords) {
    this.totalRecords = totalRecords;
    return this;
  }
}
