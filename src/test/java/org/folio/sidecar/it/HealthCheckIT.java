package org.folio.sidecar.it;

import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.HashMap;
import java.util.Map;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@IntegrationTest
@EnableWireMock(verbose = true)
public class HealthCheckIT {

  @Nested
  @TestProfile(ComprehensiveHealthCheckTestProfile.class)
  class ComprehensiveHealthCheck {

    @Test
    void healthCheck_positive() {
      TestUtils.givenJson()
        .get("/admin/health")
        .then()
        .log().ifValidationFails(LogDetail.ALL)
        .assertThat()
        .statusCode(is(SC_OK))
        .body(
          "status", is("UP"),

          "checks.find {check -> check.name == 'SmallRye Reactive Messaging - liveness check'}.status", is("UP"),
          "checks.find {check -> check.name == 'SmallRye Reactive Messaging - readiness check'}.status", is("UP"),
          "checks.find {check -> check.name == 'SmallRye Reactive Messaging - startup check'}.status", is("UP"),

          "checks.find {check -> check.name == 'Kafka connection health check'}.status", is("UP"),
          "checks.find {check -> check.name == 'Kafka connection health check'}.data.nodes", notNullValue(),

          "checks.find {check -> check.name == 'Module health check'}.status", is("UP"),
          "checks.find {check -> check.name == 'Module health check'}.data.host",
            Matchers.matchesPattern("GET http://localhost:\\d+" + TestConstants.MODULE_HEALTH_PATH)
        );
    }
  }

  @Nested
  @TestProfile(NoChecksSectionHealthCheckTestProfile.class)
  class NoChecksSectionHealthCheck {

    @Test
    void healthCheck_positive() {
      TestUtils.givenJson()
        .get("/admin/health")
        .then()
        .log().ifValidationFails(LogDetail.ALL)
        .assertThat()
        .statusCode(is(SC_OK))
        .body(
          "status", is("UP"),
          "checks", is(nullValue())
        );
    }
  }

  @Nested
  @TestProfile(SimplifiedKafkaAndModuleHealthCheckTestProfile.class)
  class SimplifiedKafkaAndModuleHealthChecks {

    @Test
    void healthCheck_positive() {
      TestUtils.givenJson()
        .get("/admin/health")
        .then()
        .log().ifValidationFails(LogDetail.ALL)
        .assertThat()
        .statusCode(is(SC_OK))
        .body(
          "status", is("UP"),

          "checks.find {check -> check.name == 'SmallRye Reactive Messaging - liveness check'}.status", is("UP"),
          "checks.find {check -> check.name == 'SmallRye Reactive Messaging - readiness check'}.status", is("UP"),
          "checks.find {check -> check.name == 'SmallRye Reactive Messaging - startup check'}.status", is("UP"),

          "checks.find {check -> check.name == 'Kafka connection health check'}.status", is("UP"),
          "checks.find {check -> check.name == 'Kafka connection health check'}.data", is(nullValue()),

          "checks.find {check -> check.name == 'Module health check'}.status", is("UP"),
          "checks.find {check -> check.name == 'Module health check'}.data", is(nullValue())
        );
    }
  }

  public static final class ComprehensiveHealthCheckTestProfile extends CommonIntegrationTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("health-check.filter.no-checks.enabled", "false");
      result.put("health-check.filter.kafka-simplified.enabled", "false");
      result.put("health-check.filter.module-simplified.enabled", "false");

      return result;
    }
  }

  public static final class NoChecksSectionHealthCheckTestProfile extends CommonIntegrationTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("health-check.filter.no-checks.enabled", "true");
      result.put("health-check.filter.kafka-simplified.enabled", "false");
      result.put("health-check.filter.module-simplified.enabled", "false");

      return result;
    }
  }

  public static final class SimplifiedKafkaAndModuleHealthCheckTestProfile extends CommonIntegrationTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("health-check.filter.no-checks.enabled", "false");
      result.put("health-check.filter.kafka-simplified.enabled", "true");
      result.put("health-check.filter.module-simplified.enabled", "true");

      return result;
    }
  }
}
