package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.succeededFuture;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT_ALL;
import static org.folio.sidecar.support.TestConstants.USER_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.users.model.User;
import org.folio.sidecar.service.token.TokenCacheFactory;
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
  @Mock private CredentialService credentialService;
  @Mock private Cache<String, TokenResponse> tokenCache;
  @Mock private TokenCacheFactory cacheFactory;
  @Mock private HttpResponse<Buffer> tokenResponse;

  @BeforeEach
  void setup() {
    when(cacheFactory.createCache()).thenReturn(tokenCache);
    service = new KeycloakImpersonationService(keycloakClient, credentialService, cacheFactory);
  }

  @Test
  void getUserToken_positive_existsInCache() {
    var token = new TokenResponse();
    var tenant = "tenant";

    when(tokenCache.getIfPresent(key(tenant, USER_ID))).thenReturn(token);
    var feature = service.getUserToken(tenant, user(USER_ID, "username"));

    assertThat(feature.succeeded()).isTrue();
    verifyNoMoreInteractions(tokenCache);
    verifyNoInteractions(keycloakClient, credentialService);
  }

  @Test
  void getUserToken_positive_retrieveKeycloak() {
    var token = new TokenResponse();
    var tenant = "tenant";
    var username = "username";
    var clientSecret = "client_secret";
    var creds = ClientCredentials.of(IMPERSONATION_CLIENT, clientSecret);

    when(tokenCache.getIfPresent(key(tenant, USER_ID))).thenReturn(null);
    when(credentialService.getImpersonationClientCredentials(tenant)).thenReturn(succeededFuture(creds));
    when(keycloakClient.impersonateUserToken(tenant, creds, username)).thenReturn(succeededFuture(tokenResponse));
    when(tokenResponse.bodyAsJson(TokenResponse.class)).thenReturn(token);
    when(tokenResponse.statusCode()).thenReturn(200);

    var feature = service.getUserToken(tenant, user(USER_ID, username));
    assertThat(feature.succeeded()).isTrue();
    verify(tokenCache).put(key(tenant, USER_ID), token);
    verifyNoMoreInteractions(tokenCache, credentialService);
    verify(keycloakClient).impersonateUserToken(tenant, creds, username);
  }

  @Test
  void getUserToken_negative_cannotImpersonateUserInKeycloak() {
    var tenant = "tenant";
    var username = "username";
    var key = tenant + ":" + USER_ID;
    var clientSecret = "client_secret";
    var creds = ClientCredentials.of(IMPERSONATION_CLIENT, clientSecret);

    when(tokenCache.getIfPresent(key)).thenReturn(null);
    when(credentialService.getImpersonationClientCredentials(tenant)).thenReturn(succeededFuture(creds));
    when(keycloakClient.impersonateUserToken(tenant, creds, username)).thenReturn(succeededFuture(tokenResponse));
    when(tokenResponse.statusCode()).thenReturn(404);

    var future = service.getUserToken(tenant, user(USER_ID, username));

    assertThat(future.failed()).isTrue();
    verify(tokenCache).getIfPresent(key);
    verify(keycloakClient, times(2)).impersonateUserToken(tenant, creds, username);
    verify(credentialService).resetImpersonationClientCredentials(tenant);
    verifyNoMoreInteractions(tokenCache, credentialService);
  }

  @Test
  void getUserToken_positive_recoveredFromOutdatedImpersonationCredentials() {
    var token = new TokenResponse();
    var tenant = "tenant";
    var username = "username";
    var outdated = ClientCredentials.of(IMPERSONATION_CLIENT, "client_secret_old");
    var creds = ClientCredentials.of(IMPERSONATION_CLIENT, "client_secret");

    when(tokenCache.getIfPresent(key(tenant, USER_ID))).thenReturn(null);

    when(credentialService.getImpersonationClientCredentials(tenant))
      .thenReturn(succeededFuture(outdated))
      .thenReturn(succeededFuture(creds));

    var unauthorized = (HttpResponse<Buffer>) mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(HttpResponseStatus.UNAUTHORIZED.code());
    when(unauthorized.bodyAsString()).thenReturn("Unauthorized");
    when(tokenResponse.bodyAsJson(TokenResponse.class)).thenReturn(token);
    when(tokenResponse.statusCode()).thenReturn(HttpResponseStatus.OK.code());

    when(keycloakClient.impersonateUserToken(tenant, outdated, username)).thenReturn(succeededFuture(unauthorized));
    when(keycloakClient.impersonateUserToken(tenant, creds, username)).thenReturn(succeededFuture(tokenResponse));

    var result = service.getUserToken(tenant, user(USER_ID, username));

    assertThat(result.succeeded()).isTrue();
    verify(tokenCache).put(key(tenant, USER_ID), token);
    verify(credentialService).resetImpersonationClientCredentials(tenant);
    verifyNoMoreInteractions(tokenCache, credentialService);
  }

  @Test
  void invalidate_positive() {
    var tenant1 = "tenant1";
    var tenant2 = "tenant2";
    var userId2 = randomUUID().toString();

    var cache = new ConcurrentHashMap<>(Map.of(
      key(tenant1, USER_ID), new TokenResponse(),
      key(tenant2, userId2), new TokenResponse(),
      "test", new TokenResponse()
    ));

    when(tokenCache.asMap()).thenReturn(cache);
    service.invalidate(LogoutEvent.of(USER_ID, null, USER_ID, LOGOUT_ALL));

    assertThat(cache.get(key(tenant1, USER_ID))).isNull();
    assertThat(cache.get(key(tenant2, userId2))).isNotNull();
    assertThat(cache.get("test")).isNotNull();
    verify(tokenCache).asMap();
  }

  @Test
  void invalidate_positive_ignoreIfLogoutEvent() {
    var tenant1 = "tenant1";
    var tenant2 = "tenant2";
    var cache = new ConcurrentHashMap<>(Map.of(
      key(tenant1, USER_ID), new TokenResponse(),
      key(tenant2, USER_ID), new TokenResponse()
    ));
    when(tokenCache.asMap()).thenReturn(cache);

    service.invalidate(LogoutEvent.of(USER_ID, randomUUID().toString(), USER_ID, LOGOUT));

    assertThat(cache.get(key(tenant1, USER_ID))).isNotNull();
    assertThat(cache.get(key(tenant2, USER_ID))).isNotNull();
    verify(tokenCache).asMap();
  }

  private static User user(String userId, String username) {
    var user = new User();
    user.setUsername(username);
    user.setId(userId);
    return user;
  }

  private static String key(String tenant, String userId) {
    return tenant + ":" + userId;
  }
}
