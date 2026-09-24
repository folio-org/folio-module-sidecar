package org.folio.sidecar.it;

import static jakarta.ws.rs.core.HttpHeaders.RETRY_AFTER;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.Matchers.is;

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
@TestProfile(RoutesNotInitializedIT.RoutesNotInitializedTestProfile.class)
@EnableWireMock(verbose = true)
class RoutesNotInitializedIT {

  @Test
  void readiness_negative_routesNotInitialized() {
    TestUtils.givenJson()
      .get("/admin/health/ready")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_SERVICE_UNAVAILABLE))
      .body(
        "status", is("DOWN"),
        "checks.find {check -> check.name == 'Routing health check'}.status", is("DOWN"),
        "checks.find {check -> check.name == 'Module health check'}.status", is("UP")
      );
  }

  @Test
  void liveness_positive_routesNotInitialized() {
    TestUtils.givenJson()
      .get("/admin/health/live")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .body("status", is("UP"));
  }

  @Test
  void handleRequest_negative_routesNotInitialized() {
    TestUtils.givenJson()
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_SERVICE_UNAVAILABLE))
      .header(RETRY_AFTER, is("5"))
      .body(
        "errors[0].code", is("routes_not_initialized_error"),
        "errors[0].message", is("Module routes are not initialized yet. Retry later")
      );
  }

  /**
   * Points the sidecar to a module that mgr-applications does not know, and keeps the bootstrap retry pending for
   * the whole test, so the sidecar never exits.
   */
  public static final class RoutesNotInitializedTestProfile extends CommonIntegrationTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("module.version", "9.9.9");
      result.put("retry.min-delay", "5m");
      result.put("retry.max-delay", "10m");
      result.put("health-check.filter.no-checks.enabled", "false");

      return result;
    }
  }
}
