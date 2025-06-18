package org.folio.sidecar.integration.cred.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@RegisterForReflection
@AllArgsConstructor(staticName = "of")
public class ClientCredentials {

  private String clientId;
  @ToString.Exclude
  private String clientSecret;
}
