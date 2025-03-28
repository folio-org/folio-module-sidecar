package org.folio.sidecar.health;

import static org.folio.sidecar.health.HealthCheckPayloadConstants.CHECKS_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.STATUS_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadUtils.copyNameAndStatus;
import static org.folio.sidecar.health.HealthCheckPayloadUtils.findCheckByName;
import static org.folio.sidecar.health.HealthCheckPayloadUtils.replaceCheckWithName;

import io.smallrye.health.api.HealthContentFilter;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RemoveDataFromNamedCheckFilter implements HealthContentFilter {

  private final String checkName;

  @Override
  public JsonObject filter(JsonObject payload) {
    return findCheckByName(payload, checkName)
      .map(check -> removeDataFromCheck(payload, check))
      .orElse(payload);
  }

  private JsonObject removeDataFromCheck(JsonObject payload, JsonObject check) {
    return Json.createObjectBuilder()
      .add(STATUS_KEY, payload.getString(STATUS_KEY))
      .add(CHECKS_KEY, replaceCheckWithName(payload.getJsonArray(CHECKS_KEY), copyNameAndStatus(check)))
      .build();
  }
}
