package org.folio.sidecar.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_REQUEST_TIMEOUT;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.USER_ID;
import static org.folio.sidecar.support.TestUtils.asJson;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.InMemoryLogHandler;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.UUID;
import java.util.logging.Formatter;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestJwtGenerator;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.hamcrest.Matchers;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(CommonIntegrationTestProfile.class)
@EnableWireMock(verbose = true)
class SidecarIT {

  private static final java.util.logging.Logger TRANSACTION_LOGGER =
    LogManager.getLogManager().getLogger("transaction");
  private static final InMemoryLogHandler MEMORY_LOG_HANDLER = new InMemoryLogHandler(
    record -> record.getLevel().intValue() >= Level.INFO.intValue());
  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  private String authToken;

  @BeforeAll
  static void beforeAll() {
    var transaction = TRANSACTION_LOGGER.getHandlers()[0];
    MEMORY_LOG_HANDLER.setFormatter(transaction.getFormatter());
    TRANSACTION_LOGGER.addHandler(MEMORY_LOG_HANDLER);
  }

  @AfterAll
  static void verifyLogs() {
    String pattern =
      "([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}:[0-9]+)\\s+-\\s+(.|\\s)+\\s+-\\s+(.|\\s)+\\[\\d{2}/\\d{2}/"
        + "\\d{4}:\\d{2}:\\d{2}:\\d{2}.+\\]\\s+(GET|POST|PUT|DELETE|OPTIONS)\\s+(.|\\s)+\\s+(.|\\s)+\\s+\\d{3}\\s+.+"
        + "\\s+rt=.+\\s+uct=.+\\s+uht=.+\\s+urt=.+\\s+.+\\s+(.|\\s)+";
    Formatter formatter = MEMORY_LOG_HANDLER.getFormatter();
    assertThat(MEMORY_LOG_HANDLER.getRecords())
      .allSatisfy(logRecord -> assertThat(formatter.format(logRecord)).matches(pattern));
  }

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
  void handleIngressRequest_positive_listOfEntitiesWithQuery() {
    var id = "f150770c-fd7c-4a3b-97b3-4e1fc51c29b3";

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities?query=id=={id}&limit={limit}", id, 1)
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is(id),
        "entities[0].name", is("Test entity (by query)"),
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
  void handleIngressRequest_positive_selfRequestWithoutToken() {
    var signature = TestUtils.getSignature();

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
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
  void handleIngressRequest_positive_duplicatedHeader() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer duplicated")
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
  void handleIngressRequest_negative_rptForbidden() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .delete("/foo/xyz")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_FORBIDDEN))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("ForbiddenException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Access Denied")
      );
  }

  @Test
  void handleIngressRequest_negative_rptUnauthorized() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .post("/foo/xyz")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_UNAUTHORIZED))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("UnauthorizedException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Unauthorized")
      );
  }

  @Test
  void handleIngressRequest_negative_rptKcError() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .put("/foo/xyz")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_FORBIDDEN))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("ForbiddenException"),
        "errors[0].code", is("authorization_error"),
        "errors[0].message", is("Access Denied")
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

  @Test
  void handleIngressRequest_negative_readTimeoutException() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/entities/d4707b3a-ca25-4b22-8406-8d712bc30b72")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_REQUEST_TIMEOUT))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("NoStackTraceTimeoutException"),
        "errors[0].code", is("read_timeout_error"),
        "errors[0].message", is("Request Timeout")
      );
  }

  @Test
  void handleIngressRequest_positive_userJwtTokenShouldBeParsedWhilePermissionsNotRequired() {
    var authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME, USER_ID);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, authToken)
      .get("/foo/bar")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .contentType(is(APPLICATION_JSON));
  }

  @Test
  void handleIngressRequest_negative_401WithWrongJwtWhilePermissionsNotRequired() {
    var authToken = TestJwtGenerator.generateExpiredJwtToken(keycloakUrl, TestConstants.TENANT_NAME);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, authToken)
      .get("/foo/bar")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_UNAUTHORIZED))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(is(asJson("json/unauthorized-error.json")));
  }

  @Test
  void handleIngressRequest_positive_emptyTokenWhilePermissionsNotRequired() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .get("/foo/bar")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON));
  }

  @Test
  void handleIngressRequest_negative_selfRequestWithInvalidToken() {
    authToken = RandomStringUtils.random(20);
    var signature = TestUtils.getSignature();
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, signature)
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
  void handleIngressRequest_positive_permissionHeaderPopulated() {
    var authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME, USER_ID);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, authToken)
      .get("/bar/foo")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .contentType(is(APPLICATION_JSON));
  }

  @Test
  void handleIngressRequest_positive_permissionHeaderRemoved() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.PERMISSIONS, "foo.bar.item.get")
      .get("/foo/bar")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON));
  }

  @Test
  void authorizeTimerRequest_negative_expiredToken() {
    authToken = TestJwtGenerator.generateExpiredJwtToken(keycloakUrl, TestConstants.TENANT_NAME);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .post("/foo/expire/timer")
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
  void authorizeTimerRequest_positive_validToken() {
    var authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TestConstants.TENANT_NAME, USER_ID);
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .post("/foo/expire/timer")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .statusCode(is(SC_OK));
  }

  @Test
  void handleIngressRequest_positive_noModuleIdHeader() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .get("/foo/no-module-id-header")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .statusCode(is(SC_OK))
      .contentType(is(APPLICATION_JSON));
  }
}
