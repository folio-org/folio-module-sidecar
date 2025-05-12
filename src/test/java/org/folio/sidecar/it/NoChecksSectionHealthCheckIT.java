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
@TestProfile(NoChecksSectionHealthCheckIT.NoChecksSectionHealthCheckTestProfile.class)
@EnableWireMock(verbose = true)
class NoChecksSectionHealthCheckIT {

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
}
