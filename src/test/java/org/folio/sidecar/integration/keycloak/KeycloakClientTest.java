package org.folio.sidecar.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.utils.TokenRequestHelper.CLIENT_CREDENTIALS_GRANT_TYPE;
import static org.folio.sidecar.utils.TokenRequestHelper.IMPERSONATION_GRANT_TYPE;
import static org.folio.sidecar.utils.TokenRequestHelper.RPT_GRANT_TYPE;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.support.TestJwtGenerator;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakClientTest {

  private static final String KEYCLOAK_URL = "http://kc:8080";
  private static final String TEST_TOKEN = TestJwtGenerator.generateJwtString(KEYCLOAK_URL, TENANT_NAME);
  private static final String TEST_CLIENT_ID = "client";
  private static final String TEST_CLIENT_SECRET = "secret";
  private static final String TEST_PERMISSION = "/foo/entries#GET";

  @Mock private WebClient webClient;
  @Mock private KeycloakProperties properties;
  @Mock private HttpRequest<Buffer> request;
  @Mock private HttpResponse<Buffer> response;

  @Captor private ArgumentCaptor<String> uriCaptor;
  @Captor private ArgumentCaptor<MultiMap> bodyCaptor;
  @Captor private ArgumentCaptor<String> tokenCaptor;

  private KeycloakClient client;

  @BeforeEach
  void setUp() {
    client = new KeycloakClient(webClient, properties);
    when(properties.getUrl()).thenReturn(KEYCLOAK_URL);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(webClient, response, response);
  }

  @Test
  void obtainToken_positive() {
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.sendForm(bodyCaptor.capture())).thenReturn(Future.succeededFuture(response));

    client.obtainToken(TENANT_NAME, ClientCredentials.of(TEST_CLIENT_ID, TEST_CLIENT_SECRET));

    var capturedRequestBody = bodyCaptor.getValue();
    assertThat(capturedRequestBody).hasSize(3);
    assertThat(capturedRequestBody.get("grant_type")).isEqualTo(CLIENT_CREDENTIALS_GRANT_TYPE);
    assertThat(capturedRequestBody.get("client_id")).isEqualTo(TEST_CLIENT_ID);
    assertThat(capturedRequestBody.get("client_secret")).isEqualTo(TEST_CLIENT_SECRET);

    assertThat(uriCaptor.getValue()).isEqualTo(
      KEYCLOAK_URL + "/realms/" + TENANT_NAME + "/protocol/openid-connect/token");
  }

  @Test
  void evaluatePermissions() {
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.bearerTokenAuthentication(tokenCaptor.capture())).thenReturn(request);
    when(request.sendForm(bodyCaptor.capture())).thenReturn(Future.succeededFuture(response));
    when(properties.getLoginClientSuffix()).thenReturn("-application");

    client.evaluatePermissions(TENANT_NAME, TEST_PERMISSION, TEST_TOKEN);

    var capturedRequestBody = bodyCaptor.getValue();
    assertThat(capturedRequestBody).hasSize(3);
    assertThat(capturedRequestBody.get("grant_type")).isEqualTo(RPT_GRANT_TYPE);
    assertThat(capturedRequestBody.get("audience")).isEqualTo(TENANT_NAME + "-application");
    assertThat(capturedRequestBody.get("permission")).isEqualTo(TEST_PERMISSION);

    assertThat(uriCaptor.getValue()).isEqualTo(
      KEYCLOAK_URL + "/realms/" + TENANT_NAME + "/protocol/openid-connect/token");

    var capturedToken = tokenCaptor.getValue();
    assertThat(capturedToken).isEqualTo(TEST_TOKEN);
  }

  @Test
  void impersonateUserToken_positive() {
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.sendForm(bodyCaptor.capture())).thenReturn(Future.succeededFuture(response));

    client.impersonateUserToken(TENANT_NAME, ClientCredentials.of(TEST_CLIENT_ID, TEST_CLIENT_SECRET), "testuser");

    var capturedRequestBody = bodyCaptor.getValue();
    assertThat(capturedRequestBody).hasSize(4);
    assertThat(capturedRequestBody.get("grant_type")).isEqualTo(IMPERSONATION_GRANT_TYPE);
    assertThat(capturedRequestBody.get("client_id")).isEqualTo(TEST_CLIENT_ID);
    assertThat(capturedRequestBody.get("client_secret")).isEqualTo(TEST_CLIENT_SECRET);
    assertThat(capturedRequestBody.get("requested_subject")).isEqualTo("testuser");

    assertThat(uriCaptor.getValue()).isEqualTo(
      KEYCLOAK_URL + "/realms/" + TENANT_NAME + "/protocol/openid-connect/token");
  }
}
