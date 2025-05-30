package org.folio.sidecar.integration.cred.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor(staticName = "of")
public class ClientCredentials {

  private String clientId;
  @ToString.Exclude
  private String clientSecret;
}
