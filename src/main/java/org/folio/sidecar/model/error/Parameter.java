package org.folio.sidecar.model.error;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class Parameter {

  private String key;
  private String value;

  public Parameter key(String key) {
    this.key = key;
    return this;
  }

  public Parameter value(String value) {
    this.value = value;
    return this;
  }
}
