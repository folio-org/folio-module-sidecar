package org.folio.sidecar.integration.keycloak.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class TokenIntrospectionResponse {

  public static final TokenIntrospectionResponse INACTIVE_TOKEN = new TokenIntrospectionResponse();

  @JsonProperty("active")
  private boolean active;

  @JsonProperty("exp")
  private Long expirationTime;
}
