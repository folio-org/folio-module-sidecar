package org.folio.sidecar.integration.cred.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor(staticName = "of")
public class UserCredentials {

  private String username;

  @ToString.Exclude
  private String password;
}
