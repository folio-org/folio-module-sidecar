package org.folio.sidecar.model.error;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class Error {

  private String type;
  private ErrorCode code;
  private String message;
  private List<Parameter> parameters;

  public Error message(String message) {
    this.message = message;
    return this;
  }

  public Error type(String type) {
    this.type = type;
    return this;
  }

  public Error code(ErrorCode code) {
    this.code = code;
    return this;
  }

  public Error parameters(List<Parameter> parameters) {
    this.parameters = parameters;
    return this;
  }
}
