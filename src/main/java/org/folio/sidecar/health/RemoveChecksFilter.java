package org.folio.sidecar.health;

import static org.folio.sidecar.health.HealthCheckPayloadConstants.CHECKS_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.STATUS_KEY;

import io.smallrye.health.api.HealthContentFilter;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class RemoveChecksFilter implements HealthContentFilter {

  @Override
  public JsonObject filter(JsonObject payload) {
    if (payload == null) {
      return null;
    }

    var result = payload;
    if (result.containsKey(CHECKS_KEY)) {
      result = Json.createObjectBuilder()
        .add(STATUS_KEY, result.getString(STATUS_KEY))
        .build();

      log.debug("Health checks under \"{}\" attribute removed from the health response", CHECKS_KEY);
    }

    return result;
  }
}
