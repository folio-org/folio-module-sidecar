package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.service.TokenCacheFactory;
import org.folio.sidecar.service.store.AsyncSecureStore;
import org.folio.sidecar.utils.SecureStoreUtils;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakImpersonationServiceTest {

  private static final String IMPERSONATION_CLIENT = "impersonation_client";

  private KeycloakImpersonationService service;

  @Mock private KeycloakClient keycloakClient;
  @Mock private AsyncSecureStore secureStore;
  @Mock private Cache<String, TokenResponse> tokenCache;
  @Mock private KeycloakProperties properties;
  @Mock private TokenCacheFactory cacheFactory;
  @Mock private HttpResponse<Buffer> tokenResponse;

  @BeforeEach
  void setup() {
    when(cacheFactory.createCache()).thenReturn(tokenCache);
    service = new KeycloakImpersonationService(keycloakClient, secureStore, cacheFactory, properties);
  }

  @Test
  void getUserToken_positive_existsInCache() {
    var token = new TokenResponse();
    var tenant = "tenant";
    var username = "username";
    var key = tenant + ":" + username;

    when(tokenCache.getIfPresent(key)).thenReturn(token);
    var feature = service.getUserToken(tenant, username);

    assertThat(feature.succeeded()).isTrue();
    verifyNoMoreInteractions(tokenCache);
    verifyNoInteractions(keycloakClient, secureStore, properties);
  }

  @Test
  void getUserToken_positive_retrieveKeycloak() {
    var token = new TokenResponse();
    var tenant = "tenant";
    var username = "username";
    var key = tenant + ":" + username;
    var secretStoreKey = SecureStoreUtils.tenantStoreKey(tenant, IMPERSONATION_CLIENT);
    var clientSecret = "client_secret";
    var creds = ClientCredentials.of(IMPERSONATION_CLIENT, clientSecret);

    when(tokenCache.getIfPresent(key)).thenReturn(null);
    when(properties.getImpersonationClient()).thenReturn(IMPERSONATION_CLIENT);
    when(secureStore.get(secretStoreKey)).thenReturn(succeededFuture(clientSecret));
    when(keycloakClient.impersonateUserToken(tenant, creds, username)).thenReturn(succeededFuture(tokenResponse));
    when(tokenResponse.bodyAsJson(TokenResponse.class)).thenReturn(token);
    when(tokenResponse.statusCode()).thenReturn(200);

    var feature = service.getUserToken(tenant, username);
    assertThat(feature.succeeded()).isTrue();
    verify(tokenCache).put(key, token);
    verifyNoMoreInteractions(tokenCache);
    verify(keycloakClient).impersonateUserToken(tenant, creds, username);
  }

  @Test
  void getUserToken_negative_cannotImpersonateUserInKeycloak() {
    var tenant = "tenant";
    var username = "username";
    var key = tenant + ":" + username;
    var secretStoreKey = SecureStoreUtils.tenantStoreKey(tenant, IMPERSONATION_CLIENT);
    var clientSecret = "client_secret";
    var creds = ClientCredentials.of(IMPERSONATION_CLIENT, clientSecret);

    when(tokenCache.getIfPresent(key)).thenReturn(null);
    when(properties.getImpersonationClient()).thenReturn(IMPERSONATION_CLIENT);
    when(secureStore.get(secretStoreKey)).thenReturn(succeededFuture(clientSecret));
    when(keycloakClient.impersonateUserToken(tenant, creds, username)).thenReturn(succeededFuture(tokenResponse));
    when(tokenResponse.statusCode()).thenReturn(404);

    var future = service.getUserToken(tenant, username);

    assertThat(future.failed()).isTrue();
    verify(tokenCache).getIfPresent(key);
    verifyNoMoreInteractions(tokenCache);
    verify(keycloakClient).impersonateUserToken(tenant, creds, username);
  }
}
