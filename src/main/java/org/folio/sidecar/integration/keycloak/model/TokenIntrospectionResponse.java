package org.folio.sidecar.integration.keycloak.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TokenIntrospectionResponse {

  public static final TokenIntrospectionResponse INACTIVE_TOKEN = new TokenIntrospectionResponse();

  @JsonProperty("active")
  private boolean active;

  @JsonProperty("exp")
  private Long expirationTime;
}
