package org.folio.sidecar.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.CHECKS_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.STATUS_KEY;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RemoveChecksFilterTest {

  private final RemoveChecksFilter filter = new RemoveChecksFilter();

  @Test
  void filter_positive_nullPayload() {
    JsonObject result = filter.filter(null);
    assertThat(result).isNull();
  }

  @Test
  void filter_positive_noChecksKey() {
    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void filter_positive_withChecksKey() {
    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .add(CHECKS_KEY, Json.createArrayBuilder().build())
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result.containsKey(CHECKS_KEY)).isFalse();
    assertThat(result.getString(STATUS_KEY)).isEqualTo("UP");
  }

  @Test
  void filter_positive_emptyPayload() {
    JsonObject payload = Json.createObjectBuilder().build();

    JsonObject result = filter.filter(payload);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void filter_positive_withAdditionalKeysRemoved() {
    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .add(CHECKS_KEY, Json.createArrayBuilder().build())
      .add("additionalKey", "additionalValue")
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result.containsKey(CHECKS_KEY)).isFalse();
    assertThat(result.containsKey("additionalKey")).isFalse();
    assertThat(result.getString(STATUS_KEY)).isEqualTo("UP");
  }
}
