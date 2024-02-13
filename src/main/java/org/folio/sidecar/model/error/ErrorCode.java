package org.folio.sidecar.model.error;

import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RegisterForReflection
@RequiredArgsConstructor
public enum ErrorCode {

  UNKNOWN_ERROR("unknown_error"),
  SERVICE_ERROR("service_error"),
  VALIDATION_ERROR("validation_error"),
  ROUTE_FOUND_ERROR("route_not_found_error"),
  UNKNOWN_TENANT("tenant_not_enabled"),
  FOUND_ERROR("found_error"),
  AUTHORIZATION_ERROR("authorization_error"),
  READ_TIMEOUT_ERROR("read_timeout_error");

  @JsonValue
  private final String value;
}
