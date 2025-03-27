package org.folio.sidecar.health;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.CHECKS_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.NAME_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.STATUS_KEY;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HealthCheckPayloadUtils {

  public static Optional<JsonObject> findCheckByName(JsonObject payload, String name) {
    if (isBlank(name)) {
      throw new IllegalArgumentException("Check name cannot be blank");
    }

    if (payload == null || !payload.containsKey(CHECKS_KEY)) {
      return Optional.empty();
    }

    return payload.getJsonArray(CHECKS_KEY).stream()
      .map(cast -> (JsonObject) cast)
      .filter(check -> hasNameWithValue(check, name))
      .findFirst();
  }

  public static JsonObject removeCheckData(JsonObject check) {
    // copy only name and status fields from the original check,
    // there shouldn't be any other fields in the check besides name, status, data
    return Json.createObjectBuilder()
      .add(NAME_KEY, check.getString(NAME_KEY))
      .add(STATUS_KEY, check.getString(STATUS_KEY))
      .build();
  }

  public static JsonArrayBuilder replaceCheckWithName(JsonArray checks, JsonObject check) {
    var checkName = check.getString(NAME_KEY);

    if (isBlank(checkName)) {
      throw new IllegalArgumentException("Check name cannot be blank");
    }

    var builder = Json.createArrayBuilder();

    for (var item : checks) {
      var chk = (JsonObject) item;

      if (hasNameWithValue(chk, checkName)) {
        builder.add(check);
      } else {
        builder.add(chk);
      }
    }

    return builder;
  }

  private static boolean hasNameWithValue(JsonObject check, String value) {
    return check.containsKey(NAME_KEY) && value.equals(check.getString(NAME_KEY));
  }
}
