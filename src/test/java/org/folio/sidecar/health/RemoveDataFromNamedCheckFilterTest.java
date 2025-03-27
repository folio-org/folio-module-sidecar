package org.folio.sidecar.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.CHECKS_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.NAME_KEY;
import static org.folio.sidecar.health.HealthCheckPayloadConstants.STATUS_KEY;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RemoveDataFromNamedCheckFilterTest {

  private static final String TEST_CHECK_NAME = "test-check";
  
  private final RemoveDataFromNamedCheckFilter filter = new RemoveDataFromNamedCheckFilter(TEST_CHECK_NAME);

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
  void filter_positive_namedCheckIsNotPresent() {
    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .add(CHECKS_KEY, Json.createArrayBuilder().build())
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void filter_positive_namedCheckIsPresent() {
    JsonObject check = Json.createObjectBuilder()
      .add(NAME_KEY, TEST_CHECK_NAME)
      .add(STATUS_KEY, "DOWN")
      .add("data", "some data")
      .build();

    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .add(CHECKS_KEY, Json.createArrayBuilder().add(check).build())
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).containsKey("data")).isFalse();
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).getString(NAME_KEY)).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).getString(STATUS_KEY)).isEqualTo("DOWN");
  }

  @Test
  void filter_positive_otherCheckIsPresentButNamedCheckIsNot() {
    JsonObject otherCheck = Json.createObjectBuilder()
      .add(NAME_KEY, "other-check")
      .add(STATUS_KEY, "UP")
      .add("data", "other data")
      .build();

    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .add(CHECKS_KEY, Json.createArrayBuilder().add(otherCheck).build())
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void filter_positive_bothNamedCheckAndOtherCheckArePresent() {
    JsonObject testCheck = Json.createObjectBuilder()
      .add(NAME_KEY, TEST_CHECK_NAME)
      .add(STATUS_KEY, "DOWN")
      .add("data", "some data")
      .build();

    JsonObject otherCheck = Json.createObjectBuilder()
      .add(NAME_KEY, "other-check")
      .add(STATUS_KEY, "UP")
      .add("data", "other data")
      .build();

    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .add(CHECKS_KEY, Json.createArrayBuilder().add(testCheck).add(otherCheck).build())
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).containsKey("data")).isFalse();
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).getString(NAME_KEY)).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).getString(STATUS_KEY)).isEqualTo("DOWN");
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(1).containsKey("data")).isTrue();
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(1).getString(NAME_KEY)).isEqualTo("other-check");
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(1).getString(STATUS_KEY)).isEqualTo("UP");
  }

  @Test
  void filter_positive_emptyPayload() {
    JsonObject payload = Json.createObjectBuilder().build();

    JsonObject result = filter.filter(payload);
    assertThat(result).isEqualTo(payload);
  }

  @Test
  void filter_positive_withAdditionalKeysRemoved() {
    JsonObject check = Json.createObjectBuilder()
      .add(NAME_KEY, TEST_CHECK_NAME)
      .add(STATUS_KEY, "DOWN")
      .add("data", "some data")
      .build();

    JsonObject payload = Json.createObjectBuilder()
      .add(STATUS_KEY, "UP")
      .add(CHECKS_KEY, Json.createArrayBuilder().add(check).build())
      .add("additionalKey", "additionalValue")
      .build();

    JsonObject result = filter.filter(payload);
    assertThat(result.containsKey(CHECKS_KEY)).isTrue();
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).containsKey("data")).isFalse();
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).getString(NAME_KEY)).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.getJsonArray(CHECKS_KEY).getJsonObject(0).getString(STATUS_KEY)).isEqualTo("DOWN");
    assertThat(result.containsKey("additionalKey")).isFalse();
  }
}
