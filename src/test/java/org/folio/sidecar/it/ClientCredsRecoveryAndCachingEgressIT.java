package org.folio.sidecar.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.utils.SecureStoreUtils.tenantStoreKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.filter.log.LogDetail;
import io.vertx.core.Future;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.integration.cred.store.AsyncSecureStore;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
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
@TestProfile(CommonIntegrationTestProfile.class)
@EnableWireMock(https = true, verbose = true)
class ClientCredsRecoveryAndCachingEgressIT {

  private static final String SUPERSECRET = "supersecret";
  private static final String OUTDATED_SECRET = "outdated_secret";

  @InjectSpy
  AsyncSecureStore secureStore;
  @Inject
  CredentialService credentialService;

  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  @ConfigProperty(name = "module.name") String moduleName;
  @ConfigProperty(name = "keycloak.service.client-id") String serviceClientId;

  String authToken;

  @BeforeEach
  void init() {
    authToken = TestJwtGenerator.generateJwtString(keycloakUrl, TENANT_NAME);
  }

  @Test
  void handleEgressRequest_positive() {
    credentialService.resetUserCredentials(TENANT_NAME, moduleName);
    credentialService.resetServiceClientCredentials(TENANT_NAME);

    when(secureStore.get(tenantStoreKey(TENANT_NAME, moduleName)))
      .thenReturn(Future.succeededFuture(OUTDATED_SECRET))
      .thenReturn(Future.succeededFuture(SUPERSECRET));

    when(secureStore.get(tenantStoreKey(TENANT_NAME, serviceClientId)))
      .thenReturn(Future.succeededFuture(OUTDATED_SECRET))
      .thenReturn(Future.succeededFuture(SUPERSECRET));

    sendRequest();

    assertUserCredentials();
    assertClientCredentials();

    sendRequest();

    assertUserCredentials();
    assertClientCredentials();
    verify(secureStore, times(2)).get(tenantStoreKey(TENANT_NAME, moduleName));
    verify(secureStore, times(2)).get(tenantStoreKey(TENANT_NAME, serviceClientId));
  }

  private void assertClientCredentials() {
    var scf = credentialService.getServiceClientCredentials(TENANT_NAME);
    assertThat(scf.succeeded()).isTrue();
    assertThat(scf.result()).isEqualTo(ClientCredentials.of(serviceClientId, SUPERSECRET));
  }

  private void assertUserCredentials() {
    var ucf = credentialService.getUserCredentials(TENANT_NAME, moduleName);
    assertThat(ucf.succeeded()).isTrue();
    assertThat(ucf.result()).isEqualTo(UserCredentials.of(moduleName, SUPERSECRET));
  }

  private void sendRequest() {
    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + authToken)
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, "dummy")
      .body("{\"name\":\"entity\",\"description\":\"An entity description\"}")
      .post("/bar/entities")
      .then()
      .log().ifValidationFails(LogDetail.ALL)
      .assertThat()
      .header(OkapiHeaders.TENANT, Matchers.is(TENANT_NAME))
      .header(TestConstants.SIDECAR_SIGNATURE_HEADER, nullValue())
      .statusCode(is(SC_CREATED))
      .contentType(is(APPLICATION_JSON))
      .body(
        "id", is("d747fc05-736e-494f-9b25-205c90d9d79a"),
        "name", is("entity"),
        "description", is("An entity description")
      );
  }
}
