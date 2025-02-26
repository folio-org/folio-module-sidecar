package org.folio.sidecar.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestJwtGenerator;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(DynamicRoutingIT.DynamicRoutingTestProfile.class)
@EnableWireMock(verbose = true)
class DynamicRoutingIT {

  private static final String MODULE_DYN_FOO_ID = "mod-dyn-foo-0.0.1";
  private static final String MODULE_DYN_FOO_NAME = "mod-dyn-foo";
  private static final String MODULE_DYN_BAR_ID = "mod-dyn-bar-0.0.1";
  private static final String MODULE_DYN_BAR_NAME = "mod-dyn-bar";

  @ConfigProperty(name = "keycloak.url")
  String keycloakUrl;
  private String authToken;

  @BeforeEach
  void init() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TENANT_NAME);
  }

  /*@Test
  void handleDynamicRequest_positive_withModuleIdHint() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TENANT_NAME)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.MODULE_HINT, MODULE_DYN_FOO_ID)
      .get("/dyn-foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, is(TENANT_NAME))
      .contentType(is(APPLICATION_JSON))
      .body(
        "entities[0].id", is("6781b843-9255-4fc4-8fe2-041141fef7c9"),
        "entities[0].name", is("Dynamic entity 1"),
        "entities[0].description", is("A dynamic entity 1 description"),
        "entities[1].id", is("f97f07f1-38f2-48c8-8070-414511c33d2d"),
        "entities[1].name", is("Dynamic entity 2"),
        "entities[1].description", is("A dynamic entity 2 description"),
        "totalRecords", is(2)
      );
  }*/

  @Test
  void handleDynamicRequest_positive_withModuleNameHint() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TENANT_NAME)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.MODULE_HINT, MODULE_DYN_FOO_NAME)
      .get("/dyn-foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, is(TENANT_NAME))
      .contentType(is(APPLICATION_JSON))
      .body(
        "entities[0].id", is("6781b843-9255-4fc4-8fe2-041141fef7c9"),
        "entities[0].name", is("Dynamic entity 1"),
        "entities[0].description", is("A dynamic entity 1 description"),
        "entities[1].id", is("f97f07f1-38f2-48c8-8070-414511c33d2d"),
        "entities[1].name", is("Dynamic entity 2"),
        "entities[1].description", is("A dynamic entity 2 description"),
        "totalRecords", is(2)
      );
  }

  @Test
  void handleDynamicRequest_negative_discoveryNotFound() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TENANT_NAME)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.MODULE_HINT, MODULE_DYN_BAR_ID)
      .get("/dyn-bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_NOT_FOUND))
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "errors[0].message", containsString("Unable to find discovery of the module with id: " + MODULE_DYN_BAR_ID),
        "total_records", is(1)
      );
  }

  @Test
  void handleDynamicRequest_negative_moduleNotEntitled() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TENANT_NAME)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.MODULE_HINT, MODULE_DYN_BAR_NAME)
      .get("/dyn-bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_INTERNAL_SERVER_ERROR))
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "errors[0].message", containsString(format("No entitled module found for name: moduleName = %s, tenant = %s",
          MODULE_DYN_BAR_NAME, TENANT_NAME)),
        "total_records", is(1)
      );
  }

  public static final class DynamicRoutingTestProfile extends CommonIntegrationTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("web-client.keycloak.tls.enabled", "false");
      result.put("web-client.egress.tls.enabled", "false");

      result.put("routing.dynamic.enabled", "true");
      result.put("routing.dynamic.discovery.cache.initial-capacity", "5");
      result.put("routing.dynamic.discovery.cache.max-size", "20");

      return result;
    }
  }
}
