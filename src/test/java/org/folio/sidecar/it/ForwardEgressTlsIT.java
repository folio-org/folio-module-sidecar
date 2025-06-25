package org.folio.sidecar.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_REQUEST_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.USER_TOKEN;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.InMemoryLogHandler;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.log.LogDetail;
import java.util.logging.Formatter;
import lombok.SneakyThrows;
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
@EnableWireMock(https = true, verbose = true)
class ForwardEgressTlsIT {

  private static final java.util.logging.Logger TRANSACTION_LOGGER =
    LogManager.getLogManager().getLogger("transaction");
  private static final InMemoryLogHandler MEMORY_LOG_HANDLER = new InMemoryLogHandler(
    record -> record.getLevel().intValue() >= Level.INFO.intValue());
  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  private String authToken;

  @BeforeAll
  @SneakyThrows
  static void beforeAll() {
    Thread.sleep(2000);
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
  void handleEgressRequest_negative_readTimeoutException() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.TOKEN, USER_TOKEN)
      .body("{\"name\":\"entity-timeout\",\"description\":\"Test description\"}")
      .post("/bar/entities")
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
  void handleEgressRequest_positive_multipleInterfaceType() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.MODULE_ID, "mod-qux-0.0.2")
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .header(OkapiHeaders.TOKEN, USER_TOKEN)
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
      .delete("/bar/entities")
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
        "errors[0].message", is("Route is not found [method: DELETE, path: /bar/entities]")
      );
  }

  @Test
  void handleEgressRequest_positive() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(OkapiHeaders.TOKEN, USER_TOKEN)
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
  void handleEgressRequest_positive_listOfEntities() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .header(OkapiHeaders.TOKEN, USER_TOKEN)
      .get("/bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .contentType(is(APPLICATION_JSON))
      .body(
        "totalRecords", is(1),
        "entities[0].id", is("d747fc05-736e-494f-9b25-205c90d9d79a"),
        "entities[0].name", is("entity"),
        "entities[0].description", is("An entity description")
      );
  }

  @Test
  void handleEgressRequest_positive_listOfEntitiesWithQuery() {
    var id = "f150770c-fd7c-4a3b-97b3-4e1fc51c29b3";

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .header(OkapiHeaders.TOKEN, USER_TOKEN)
      .get("/bar/entities?query=id=={id}&limit={limit}", id, 1)
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
        "entities[0].name", is("entity (by query)"),
        "entities[0].description", is("An entity description")
      );
  }

  @Test
  void handleEgressRequest_positive_xOkapiPermissionsPopulated() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.TOKEN, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .post("/dez/items")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_CREATED))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .body("message", is("permissions sets in x-okapi-permissions"));
  }

  @Test
  void handleEgressRequest_positive_responseWithTimeHeader() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .header(OkapiHeaders.TOKEN, USER_TOKEN)
      .get("/bar/entities")
      .then()
      .log().headers()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .statusCode(is(SC_OK))
      .header(OkapiHeaders.TENANT, Matchers.is(TestConstants.TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .header("x-response-time", Matchers.matchesPattern("\\d+ms"))
      .contentType(is(APPLICATION_JSON));
  }

  @Test
  void handleEgressRequest_negative_emptySystemUserToken_whenSystemUserIsNotDefinedByModule() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TestConstants.TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .body("{\"name\":\"entity\",\"description\":\"An entity description\"}")
      .post("/bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_BAD_REQUEST))
      .contentType(is(APPLICATION_JSON))
      .body(
        "total_records", is(1),
        "errors[0].type", is("BadRequestException"),
        "errors[0].code", is("service_error"),
        "errors[0].message", is("System user token is required if the request doesn't contain X-Okapi-Token. "
          + "Check that system user is configured for the module: " + MODULE_ID)
      );
  }
}
