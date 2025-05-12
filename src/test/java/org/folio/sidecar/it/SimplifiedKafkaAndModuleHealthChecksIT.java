package org.folio.sidecar.it;

import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.HashMap;
import java.util.Map;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(SimplifiedKafkaAndModuleHealthChecksIT.SimplifiedKafkaAndModuleHealthCheckTestProfile.class)
@EnableWireMock(verbose = true)
class SimplifiedKafkaAndModuleHealthChecksIT {

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
