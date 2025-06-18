package org.folio.sidecar.integration.keycloak.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class TokenResponse {

  @JsonProperty("access_token")
  protected String accessToken;

  @JsonProperty("refresh_token")
  protected String refreshToken;

  @JsonProperty("expires_in")
  protected Long expiresIn;
}
