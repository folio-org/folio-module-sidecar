package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.model.ClientCredentials.of;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.LOGIN_CLIENT_CREDENTIALS;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.security.UnauthorizedException;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.model.UserCredentials;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakServiceTest {

  private static final String ADMIN_CLIENT_ID = "folio-backend-admin";
  private static final String ADMIN_CLIENT_SECRET = "admin_secret";
  private static final String SERVICE_CLIENT_ID = "sidecar-module-access-client";
  private static final String SERVICE_CLIENT_SECRET = "secret";
  private static final ClientCredentials SERVICE_CLIENT_CREDENTIALS = of(SERVICE_CLIENT_ID, SERVICE_CLIENT_SECRET);
  private static final String REFRESH_TOKEN = "refresh";
  private static final String SUPER_TENANT = "master";
  private static final String TEST_USER = "testuser";
  private static final String TEST_USER_PASSWORD = "password";
  private static final ClientCredentials ADMIN_CLIENT_CREDENTIALS = of(ADMIN_CLIENT_ID, ADMIN_CLIENT_SECRET);
  private static final TokenResponse TOKEN_RESPONSE = new TokenResponse(AUTH_TOKEN, REFRESH_TOKEN, 10L);
  private static final UserCredentials TEST_USER_CREDENTIALS = UserCredentials.of(TEST_USER, TEST_USER_PASSWORD);

  @Mock private KeycloakClient keycloakClient;
  @Mock private ErrorHandler errorHandler;
  @Mock private HttpResponse<Buffer> httpResponse;
  @Mock private RoutingContext rc;

  @InjectMocks private KeycloakService service;

  @Test
  void obtainUserToken_positive() {
    when(httpResponse.bodyAsJson(TokenResponse.class)).thenReturn(TOKEN_RESPONSE);
    when(keycloakClient.obtainUserToken(any(), any(), any())).thenReturn(succeededFuture(httpResponse));
    when(httpResponse.statusCode()).thenReturn(SC_OK);

    var future = service.obtainUserToken(SUPER_TENANT, LOGIN_CLIENT_CREDENTIALS, TEST_USER_CREDENTIALS);

    assertThat(future.succeeded()).isTrue();
    assertThat(future.result()).isEqualTo(TOKEN_RESPONSE);
    verify(keycloakClient).obtainUserToken(SUPER_TENANT, LOGIN_CLIENT_CREDENTIALS, TEST_USER_CREDENTIALS);
    verifyNoInteractions(errorHandler);
  }

  @Test
  void refreshUserToken_positive() {
    when(httpResponse.bodyAsJson(TokenResponse.class)).thenReturn(TOKEN_RESPONSE);
    when(keycloakClient.refreshUserToken(any(), any(), any())).thenReturn(succeededFuture(httpResponse));
    when(httpResponse.statusCode()).thenReturn(SC_OK);

    var future = service.refreshUserToken(SUPER_TENANT, LOGIN_CLIENT_CREDENTIALS, REFRESH_TOKEN);

    assertThat(future.succeeded()).isTrue();
    assertThat(future.result()).isEqualTo(TOKEN_RESPONSE);
    verify(keycloakClient).refreshUserToken(SUPER_TENANT, LOGIN_CLIENT_CREDENTIALS, REFRESH_TOKEN);
    verifyNoInteractions(errorHandler);
  }

  @Test
  void obtainToken_positive() {
    when(httpResponse.bodyAsJson(TokenResponse.class)).thenReturn(TOKEN_RESPONSE);
    when(keycloakClient.obtainToken(any(), any())).thenReturn(succeededFuture(httpResponse));
    when(httpResponse.statusCode()).thenReturn(SC_OK);

    var future = service.obtainToken(SUPER_TENANT, ADMIN_CLIENT_CREDENTIALS);

    assertThat(future.succeeded()).isTrue();
    assertThat(future.result()).isEqualTo(TOKEN_RESPONSE);
    verify(keycloakClient).obtainToken(SUPER_TENANT, ADMIN_CLIENT_CREDENTIALS);
    verifyNoInteractions(errorHandler);
  }

  @Test
  void obtainToken_positive_withRc() {

    when(httpResponse.bodyAsJson(TokenResponse.class)).thenReturn(TOKEN_RESPONSE);
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(keycloakClient.obtainToken(any(), any())).thenReturn(succeededFuture(httpResponse));

    var future = service.obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS, rc);

    assertThat(future.succeeded()).isTrue();
    assertThat(future.result()).isEqualTo(TOKEN_RESPONSE);
    verify(keycloakClient).obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS);
    verifyNoInteractions(errorHandler);
  }

  @Test
  void obtainToken_negative_failed_kcRequest() {
    when(keycloakClient.obtainToken(any(), any())).thenReturn(
      failedFuture(new ServerErrorException(SC_SERVICE_UNAVAILABLE)));

    var future = service.obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS, rc);

    assertTrue(future.failed());
    verify(keycloakClient).obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS);
    verify(errorHandler).sendErrorResponse(eq(rc), any(WebApplicationException.class));
  }

  @Test
  void obtainToken_negative_unauthorized() {
    when(httpResponse.statusCode()).thenReturn(SC_UNAUTHORIZED);
    when(keycloakClient.obtainToken(any(), any())).thenReturn(succeededFuture(httpResponse));

    var future = service.obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS, rc);

    assertTrue(future.failed());
    verify(keycloakClient).obtainToken(TENANT_NAME, of(SERVICE_CLIENT_ID, SERVICE_CLIENT_SECRET));
    verify(errorHandler).sendErrorResponse(eq(rc), any(UnauthorizedException.class));
  }
}
