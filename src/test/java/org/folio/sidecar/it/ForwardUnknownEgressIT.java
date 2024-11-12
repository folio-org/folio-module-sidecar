package org.folio.sidecar.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.it.ForwardUnknownEgressIT.ForwardUnknownTestProfile;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestJwtGenerator;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(ForwardUnknownTestProfile.class)
@EnableWireMock(verbose = true)
class ForwardUnknownEgressIT {

  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  private String authToken;

  @BeforeEach
  void init() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME);
  }

  @Test
  void handleEgressRequest_positive() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .body("{\"name\":\"entity\",\"description\":\"An entity description\"}")
      .post("/bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .statusCode(is(SC_CREATED))
      .contentType(is(APPLICATION_JSON))
      .body(
        "id", is("d747fc05-736e-494f-9b25-205c90d9d79a"),
        "name", is("entity"),
        "description", is("An entity description")
      );
  }

  @Test
  void handleEgressRequest_positive_multipleInterfaceType() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.MODULE_ID, "mod-qux-0.0.2")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .statusCode(is(SC_OK))
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(2),
        "entities[0].id", is("d22c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity 1"),
        "entities[0].description", is("A Test entity 1 description"),
        "entities[1].id", is("d23c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[1].name", is("Test entity 2"),
        "entities[1].description", is("A Test entity 2 description")
      );
  }

  @Test
  void handleEgressRequest_positive_forwardUnknown() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "test-signature")
      .get("/baz/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_OK))
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d22c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity 3"),
        "entities[0].description", is("A Test entity 3 description")
      );
  }

  public static final class ForwardUnknownTestProfile extends CommonIntegrationTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("sidecar.forward-unknown-requests", "true");
      result.put("web-client.keycloak.tls.enabled", "false");
      result.put("web-client.gateway.tls.enabled", "false");
      result.put("web-client.egress.tls.enabled", "false");

      return result;
    }
  }
}
