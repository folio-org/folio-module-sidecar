package org.folio.sidecar.integration.kafka;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@RegisterForReflection
public class LogoutEvent {

  private String userId;
  private String sessionId;
  private String keycloakUserId;
  private Type type;

  public enum Type {
    LOGOUT,
    LOGOUT_ALL
  }
}
