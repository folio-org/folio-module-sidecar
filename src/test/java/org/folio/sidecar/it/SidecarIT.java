package org.folio.sidecar.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.filter.log.LogDetail;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
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
@QuarkusTestResource(WireMockExtension.class)
class SidecarIT {

  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  private String authToken;

  @BeforeEach
  void init() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME);
  }

  @Test
  void handleIngressRequest_positive_listOfEntities() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
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
  void handleIngressRequest_positive_selfRequest() {
    var signature = TestUtils.getSignature();

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
  void handleIngressRequest_positive_entityById() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities/d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "id", is("d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "name", is("Test entity"),
        "description", is("A Test entity description")
      );
  }

  @Test
  void handleIngressRequest_positive_systemToken() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.SYSTEM_TOKEN, authToken)
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
  void handleIngressRequest_positive_appNotEnabledForTenant() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, "unknown");
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, "unknown")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_BAD_REQUEST))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("TenantNotEnabledException"),
        "errors[0].code", is("tenant_not_enabled"),
        "errors[0].message", is("Application is not enabled for tenant: unknown")
      );
  }

  @Test
  void handleIngressRequest_positive_appNotEnabledForTenant_tenantInstallOp() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, "newtenant");
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, "newtenant")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .post("/_/tenant")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_NO_CONTENT));
  }

  @Test
  void handleIngressRequest_negative_requestNotMatched() {
    var id = UUID.randomUUID();
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/users/" + id)
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_NOT_FOUND))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("NotFoundException"),
        "errors[0].code", is("route_not_found_error"),
        "errors[0].message", is(String.format("Route is not found [method: GET, path: /foo/users/%s]", id))
      );
  }

  /**
   * This scenario is reproduced during tenant install. When a module (for example mod-circulation) registers events in
   * mod-pubsub the request is sent without x-okapi-token (as we don't pass it during tenant install), mod-pubsub-client
   * lib adds a default "dummy" token to such requests and therefore sidecar should be able to process such request.
   */
  @Test
  void handleIngressRequest_positive_dummyAuthTokenAndValidSysToken() {
    var sysToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, "dummy")
      .header(OkapiHeaders.SYSTEM_TOKEN, sysToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d12c8c0c-d387-4bd5-9ad6-c02b41abe4ec"),
        "entities[0].name", is("Test entity"),
        "entities[0].description", is("A Test entity description")
      );
  }

  @Test
  void handleIngressRequest_negative_expiredToken() {
    authToken = TestJwtGenerator.generateExpiredJwtToken(keycloakUrl, TestConstants.TENANT_NAME);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_UNAUTHORIZED))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("UnauthorizedException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Unauthorized")
      );
  }

  @Test
  void handleIngressRequest_negative_invalidTokenIssuer() {
    authToken = TestJwtGenerator.generateJwtString("http://kc", TestConstants.TENANT_NAME);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_UNAUTHORIZED))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("UnauthorizedException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Unauthorized")
      );
  }

  @Test
  void handleIngressRequest_negative_invalidTokenTenant() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, "unknown");
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_UNAUTHORIZED))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("UnauthorizedException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Unauthorized")
      );
  }

  @Test
  void handleIngressRequest_negative_invalidSysTokenTenant() {
    var systemToken = TestJwtGenerator.generateJwtString(keycloakUrl, "unknown");
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, authToken)
      .header(OkapiHeaders.SYSTEM_TOKEN, systemToken)
      .get("/foo/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_UNAUTHORIZED))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("UnauthorizedException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Unauthorized")
      );
  }

  @Test
  void handleEgressRequest_positive() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .body("{\"name\":\"entity\",\"description\":\"An entity description\"}")
      .post("/bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
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
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .get("/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
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
  void handleEgressRequest_negative_routeNotFoundForInvalidHttpMethod() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .get("/bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_NOT_FOUND))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("NotFoundException"),
        "errors[0].code", is("route_not_found_error"),
        "errors[0].message", is("Route is not found [method: GET, path: /bar/entities]")
      );
  }

  @Test
  void moduleHealthCheck_up() {
    TestUtils.givenJson()
      .get("/admin/health")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .body(
        "status", is("UP"),
        "checks.find {check -> check.name == 'Module health check'}.status", is("UP"),
        "checks.find {check -> check.name == 'Module health check'}.data.host",
        Matchers.matchesPattern("GET http://localhost:\\d+" + TestConstants.MODULE_HEALTH_PATH)
      );
  }
}
