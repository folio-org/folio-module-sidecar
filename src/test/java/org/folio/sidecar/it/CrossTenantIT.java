package org.folio.sidecar.it;

import static io.restassured.filter.log.LogDetail.ALL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.it.CrossTenantIT.CrossTenantTestProfile;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestJwtGenerator;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.WireMockExtension;
import org.folio.support.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@IntegrationTest
@QuarkusTest
@TestProfile(CrossTenantTestProfile.class)
class CrossTenantIT {

  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";

  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  private String authToken;

  @BeforeEach
  void init() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, "another-tenant", USER_ID);
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

    WireMockExtension.wireMockServer.removeStubMapping(mockId);
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

    WireMockExtension.wireMockServer.removeStubMapping(mockId);
  }

  @Test
  void handleIngressRequest_positive_crossTenantEnabled_systemToken() {
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

    WireMockExtension.wireMockServer.removeStubMapping(mockId);
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

    WireMockExtension.wireMockServer.removeStubMapping(mockId);
  }

  @Test
  void handleIngressRequest_negative_crossTenantEnabled_userNotExistForTargetTenant() {
    var userId = "12300000-0000-0000-0000-000000000123";
    var userToken = TestJwtGenerator.generateJwtString(keycloakUrl, "another-tenant", userId);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, userToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_FORBIDDEN))
      .contentType(is(APPLICATION_JSON))
      .body("total_records", is(1),
        "errors[0].type", is("ForbiddenException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Access Denied")
      );
  }

  @Test
  void handleIngressRequest_negative_crossTenantEnabledAndUserIdClaimIsNotFound() {
    var userToken = TestJwtGenerator.generateJwtString(keycloakUrl, "another-tenant");
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, userToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(ALL)
      .assertThat()
      .statusCode(is(SC_FORBIDDEN))
      .contentType(is(APPLICATION_JSON))
      .body("total_records", is(1),
        "errors[0].type", is("ForbiddenException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Access Denied")
      );
  }

  @Test
  void handleIngressRequest_positive_skipImpersonationIfSelfRequest() {
    var signature = TestUtils.getSignature();
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, "newtenant");

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

  private UUID addObtainTokenRequestToWiremock() {
    var id = UUID.randomUUID();
    var sessionState = UUID.randomUUID();
    var accessToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME, USER_ID, sessionState);
    var refreshToken =
      TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME, USER_ID, sessionState);
    var mappingsTemplate = TestUtils.readString("/mappings-templates/keycloak/obtain-token-template.json");

    var obtainTokenRequestStubMappingJson = mappingsTemplate
      .replace("{{id}}", id.toString())
      .replace("{{accessToken}}", accessToken)
      .replace("{{refreshToken}}", refreshToken)
      .replace("{{tenant}}", TestConstants.TENANT_NAME)
      .replace("{{sessionState}}", sessionState.toString());

    var stubMapping = StubMapping.buildFrom(obtainTokenRequestStubMappingJson);
    WireMockExtension.wireMockServer.addStubMapping(stubMapping);

    return stubMapping.getId();
  }

  public static class CrossTenantTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
        "sidecar.cross-tenant.enabled", "true"
      );
    }
  }
}
