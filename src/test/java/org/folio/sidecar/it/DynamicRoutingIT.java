package org.folio.sidecar.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_OK;
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
public class DynamicRoutingIT {

  private static final String MODULE_DYNAMIC_ID = "mod-dynamic-0.0.1";
  private static final String MODULE_DYNAMIC_NAME = "mod-dynamic";

  @ConfigProperty(name = "keycloak.url")
  String keycloakUrl;
  private String authToken;

  @BeforeEach
  void init() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME);
  }

  @Test
  void handleDynamicRequest_positive_withModuleIdHint() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.MODULE_HINT, MODULE_DYNAMIC_ID)
      .get("/dynamic/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, is(TestConstants.TENANT_NAME))
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
  void handleDynamicRequest_positive_withModuleNameHint() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.MODULE_HINT, MODULE_DYNAMIC_NAME)
      .get("/dynamic/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, is(TestConstants.TENANT_NAME))
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
