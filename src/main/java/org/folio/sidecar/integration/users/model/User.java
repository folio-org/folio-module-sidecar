package org.folio.sidecar.integration.users.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class User {

  private String id;
  private String username;
}
