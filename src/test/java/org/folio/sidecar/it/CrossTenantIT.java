package org.folio.sidecar.it;

import static io.restassured.filter.log.LogDetail.ALL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.folio.sidecar.support.TestJwtGenerator.generateJwtString;
import static org.folio.sidecar.support.TestUtils.asJson;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.it.CrossTenantIT.CrossTenantTestProfile;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InjectWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(CrossTenantTestProfile.class)
@EnableWireMock(verbose = true)
class CrossTenantIT {

  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";

  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  @InjectWireMock WireMockServer wireMockServer;
  private String authToken;

  @BeforeAll
  @SneakyThrows
  static void beforeAll() {
    Thread.sleep(2000);
  }

  @BeforeEach
  void init() {
    authToken = generateJwtString(keycloakUrl, "another-tenant", USER_ID);
  }

  @Test
  void handleIngressRequest_tokenInAuthorizationHeader() {
    var mockId = addObtainTokenRequestToWiremock();

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity"),
        "entities[0].description", is("A Test entity description")
      );

    wireMockServer.removeStubMapping(mockId);
  }

  @Test
  void handleIngressRequest_positive_tokenInOkapiHeaders() {
    var mockId = addObtainTokenRequestToWiremock();

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity"),
        "entities[0].description", is("A Test entity description")
      );

    wireMockServer.removeStubMapping(mockId);
  }

  @Test
  void handleIngressRequest_positive_crossTenantEnabled_systemToken() {
    var mockId = addObtainTokenRequestToWiremock();

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.SYSTEM_TOKEN, authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity"),
        "entities[0].description", is("A Test entity description")
      );

    wireMockServer.removeStubMapping(mockId);
  }

  @Test
  void handleIngressRequest_positive_crossTenantEnabled_impersonationUserToken() {
    var mockId = addObtainTokenRequestToWiremock();

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity"),
        "entities[0].description", is("A Test entity description")
      );

    wireMockServer.removeStubMapping(mockId);
  }

  @Test
  void handleIngressRequest_negative_crossTenantEnabled_userNotExistForTargetTenant() {
    var userId = "12300000-0000-0000-0000-000000000123";
    var userToken = generateJwtString(keycloakUrl, "another-tenant", userId);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, userToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_FORBIDDEN))
      .contentType(is(APPLICATION_JSON))
      .body(is(asJson("json/forbidden-error.json")));
  }

  @Test
  void handleIngressRequest_negative_crossTenantEnabledAndUserIdClaimIsNotFound() {
    var userTokenWithoutUserIdClaim = generateJwtString(keycloakUrl, "another-tenant");
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, userTokenWithoutUserIdClaim)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_UNAUTHORIZED))
      .contentType(is(APPLICATION_JSON))
      .body(is(asJson("json/unauthorized-error.json")));
  }

  @Test
  void handleIngressRequest_positive_skipImpersonationIfSelfRequest() {
    var signature = TestUtils.getSignature();
    authToken = generateJwtString(keycloakUrl, "newtenant");

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, signature)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity"),
        "entities[0].description", is("A Test entity description")
      );
  }

  @Test
  void handleIngressRequest_negative_crossTenantEnabledAndUserTokenSessionTerminated() {
    var tokenWithTerminatedSession = generateJwtString(keycloakUrl, "foo-tenant", USER_ID);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, tokenWithTerminatedSession)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_UNAUTHORIZED))
      .contentType(is(APPLICATION_JSON))
      .body(is(asJson("json/unauthorized-error.json")));
  }

  private UUID addObtainTokenRequestToWiremock() {
    var id = UUID.randomUUID();
    var sessionState = UUID.randomUUID();
    var accessToken = generateJwtString(keycloakUrl, TestConstants.TENANT_NAME, USER_ID, sessionState);
    var refreshToken =
      generateJwtString(keycloakUrl, TestConstants.TENANT_NAME, USER_ID, sessionState);
    var mappingsTemplate = TestUtils.readString("/mappings-templates/keycloak/obtain-token-template.json");

    var obtainTokenRequestStubMappingJson = mappingsTemplate
      .replace("{{id}}", id.toString())
      .replace("{{accessToken}}", accessToken)
      .replace("{{refreshToken}}", refreshToken)
      .replace("{{tenant}}", TestConstants.TENANT_NAME)
      .replace("{{sessionState}}", sessionState.toString());

    var stubMapping = StubMapping.buildFrom(obtainTokenRequestStubMappingJson);
    wireMockServer.addStubMapping(stubMapping);

    return stubMapping.getId();
  }

  public static final class CrossTenantTestProfile extends CommonIntegrationTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("sidecar.cross-tenant.enabled", "true");

      return result;
    }
  }
}
