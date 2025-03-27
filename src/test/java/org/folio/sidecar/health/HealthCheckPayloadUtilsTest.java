package org.folio.sidecar.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.sidecar.health.HealthCheckPayloadUtils.findCheckByName;
import static org.folio.sidecar.health.HealthCheckPayloadUtils.removeCheckData;
import static org.folio.sidecar.health.HealthCheckPayloadUtils.replaceCheckWithName;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class HealthCheckPayloadUtilsTest {

  private static final String TEST_CHECK_NAME = "test-check";
  private static final String OTHER_CHECK_NAME = "other-check";

  @Test
  void findCheckByName_positive_nullPayload() {
    var result = findCheckByName(null, TEST_CHECK_NAME);
    assertThat(result).isEmpty();
  }

  @Test
  void findCheckByName_positive_noChecksKey() {
    var payload = Json.createObjectBuilder()
      .add("status", "UP")
      .build();

    var result = findCheckByName(payload, TEST_CHECK_NAME);
    assertThat(result).isEmpty();
  }

  @Test
  void findCheckByName_positive_namedCheckIsNotPresent() {
    var check = createCheck(OTHER_CHECK_NAME, "UP");

    var payload = Json.createObjectBuilder()
      .add("status", "UP")
      .add("checks", Json.createArrayBuilder().add(check).build())
      .build();

    var result = findCheckByName(payload, TEST_CHECK_NAME);
    assertThat(result).isEmpty();
  }

  @Test
  void findCheckByName_positive_namedCheckIsPresent() {
    var check = createCheck(TEST_CHECK_NAME, "DOWN");

    var payload = Json.createObjectBuilder()
      .add("status", "UP")
      .add("checks", Json.createArrayBuilder().add(check).build())
      .build();

    var result = findCheckByName(payload, TEST_CHECK_NAME);
    assertThat(result).isPresent();
    assertThat(result.get().getString("name")).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.get().getString("status")).isEqualTo("DOWN");
  }

  @Test
  void findCheckByName_positive_multipleChecks() {
    var testCheck = createCheck(TEST_CHECK_NAME, "DOWN");
    var otherCheck = createCheck(OTHER_CHECK_NAME, "UP");

    var checks = Json.createArrayBuilder().add(testCheck).add(otherCheck);

    var payload = Json.createObjectBuilder()
      .add("status", "UP")
      .add("checks", checks.build())
      .build();

    var result = findCheckByName(payload, TEST_CHECK_NAME);
    assertThat(result).isPresent();
    assertThat(result.get().getString("name")).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.get().getString("status")).isEqualTo("DOWN");
  }

  @Test
  void findCheckByName_negative_blankName() {
    assertThatThrownBy(() -> findCheckByName(Json.createObjectBuilder().build(), ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Check name cannot be blank");
  }

  @Test
  void findCheckByName_negative_nameIsNotString() {
    var check = Json.createObjectBuilder()
      .add("name", 1)
      .add("status", "DOWN")
      .build();

    var payload = Json.createObjectBuilder()
      .add("status", "UP")
      .add("checks", Json.createArrayBuilder().add(check).build())
      .build();

    assertThatThrownBy(() -> findCheckByName(payload, TEST_CHECK_NAME))
      .isInstanceOf(ClassCastException.class)
      .hasMessageContaining("cannot be cast to class jakarta.json.JsonString");
  }

  @Test
  void replaceCheckWithName_namedCheckIsNotPresent() {
    var checks = Json.createArrayBuilder().add(createCheck(OTHER_CHECK_NAME, "UP")).build();
    var check = createCheck(TEST_CHECK_NAME, "DOWN");

    var result = replaceCheckWithName(checks, check).build();
    assertThat(result).hasSize(1);
    assertThat(result.getJsonObject(0).getString("name")).isEqualTo(OTHER_CHECK_NAME);
  }

  @Test
  void replaceCheckWithName_namedCheckIsPresent() {
    var checks = Json.createArrayBuilder().add(createCheck(TEST_CHECK_NAME, "UP")).build();
    var check = createCheck(TEST_CHECK_NAME, "DOWN");

    var result = replaceCheckWithName(checks, check).build();
    assertThat(result).hasSize(1);
    assertThat(result.getJsonObject(0).getString("name")).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.getJsonObject(0).getString("status")).isEqualTo("DOWN");
  }

  @Test
  void replaceCheckWithName_multipleChecks() {
    var checks = Json.createArrayBuilder()
      .add(createCheck(TEST_CHECK_NAME, "UP"))
      .add(createCheck(OTHER_CHECK_NAME, "UP"))
      .build();
    var check = createCheck(TEST_CHECK_NAME, "DOWN");

    var result = replaceCheckWithName(checks, check).build();
    assertThat(result).hasSize(2);
    assertThat(result.getJsonObject(0).getString("name")).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.getJsonObject(0).getString("status")).isEqualTo("DOWN");
    assertThat(result.getJsonObject(1).getString("name")).isEqualTo(OTHER_CHECK_NAME);
    assertThat(result.getJsonObject(1).getString("status")).isEqualTo("UP");
  }

  @Test
  void replaceCheckWithName_negative_blankCheckName() {
    var checks = Json.createArrayBuilder().add(createCheck(OTHER_CHECK_NAME, "UP")).build();
    var check = createCheck("", "DOWN");

    assertThatThrownBy(() -> replaceCheckWithName(checks, check))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Check name cannot be blank");
  }

  @Test
  void removeCheckData_positive() {
    var check = Json.createObjectBuilder()
      .add("name", TEST_CHECK_NAME)
      .add("status", "DOWN")
      .add("data", Json.createObjectBuilder().add("key1", "value1").add("key2", "value2"))
      .build();

    var result = removeCheckData(check);
    assertThat(result.getString("name")).isEqualTo(TEST_CHECK_NAME);
    assertThat(result.getString("status")).isEqualTo("DOWN");
    assertThat(result.containsKey("data")).isFalse();
  }

  private static JsonObject createCheck(String name, String status) {
    return Json.createObjectBuilder()
      .add("name", name)
      .add("status", status)
      .build();
  }
}
