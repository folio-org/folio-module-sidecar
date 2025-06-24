package org.folio.sidecar.service.token;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.util.Collections.emptyMap;
import static java.util.Map.entry;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.sidecar.support.TestConstants.LOGIN_CLIENT_CREDENTIALS;
import static org.folio.sidecar.support.TestConstants.MODULE_NAME;
import static org.folio.sidecar.support.TestConstants.REQUEST_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TOKEN_RESPONSE;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.UnauthorizedException;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.service.routing.ModuleBootstrapListener;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SystemUserTokenProviderTest {

  private static final UserCredentials TEST_USER = UserCredentials.of(MODULE_NAME, "testpwd");

  @Mock private KeycloakService keycloakService;
  @Mock private CredentialService credentialService;
  @Mock private ModuleProperties moduleProperties;
  @Mock private AsyncTokenCacheFactory cacheFactory;
  @Mock private RoutingContext rc;
  @Mock private HttpServerRequest request;

  @Mock private AsyncLoadingCache<String, TokenResponse> tokenCache;
  private SystemUserTokenProvider service;

  @BeforeEach
  void setup() {
    when(cacheFactory.createCache(ArgumentMatchers.any())).thenReturn(tokenCache);
    service = new SystemUserTokenProvider(keycloakService, credentialService, moduleProperties, cacheFactory);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(keycloakService, credentialService, moduleProperties, cacheFactory, tokenCache);
  }

  @Nested
  class WhenSystemUserRequired {

    @BeforeEach
    void setup() {
      var mb = new ModuleBootstrapDiscovery();
      mb.setSystemUserRequired(true);

      service.onModuleBootstrap(mb, ModuleBootstrapListener.ChangeType.INIT);
    }

    @Test
    void syncCache_positive() {
      var cached = Map.ofEntries(
        entry("tenant-foo", completedFuture(new TokenResponse())),
        entry("tenant-bar", completedFuture(new TokenResponse()))
      );
      when(tokenCache.asMap()).thenReturn(new ConcurrentHashMap<>(cached));

      var loadingCache = mock(LoadingCache.class);
      when(tokenCache.synchronous()).thenReturn(loadingCache);

      ArgumentCaptor<List<String>> invalidateCaptor = ArgumentCaptor.captor();
      doNothing().when(loadingCache).invalidateAll(invalidateCaptor.capture());

      ArgumentCaptor<List<String>> refreshCaptor = ArgumentCaptor.captor();
      when(tokenCache.getAll(refreshCaptor.capture())).thenReturn(completedFuture(emptyMap()));

      service.syncCache(EntitlementsEvent.of(Set.of("tenant-foo", "tenant-baz", "tenant-qux")));

      assertThat(invalidateCaptor.getValue()).containsExactly("tenant-bar");
      assertThat(refreshCaptor.getValue()).containsExactlyInAnyOrder("tenant-baz", "tenant-qux");
    }

    @Test
    void getToken_positive() {
      mockRequest();
      when(tokenCache.get(TENANT_NAME)).thenReturn(completedFuture(TOKEN_RESPONSE));

      var tf = service.getToken(rc);

      assertThat(tf.succeeded()).isTrue();
      assertThat(tf.result()).hasValue(TOKEN_RESPONSE.getAccessToken());
    }

    @Test
    void getToken_negative_cacheError() {
      mockRequest();
      when(tokenCache.get(TENANT_NAME)).thenReturn(CompletableFuture.failedFuture(
        new RuntimeException("Failed to get token")));

      var tf = service.getToken(rc);

      assertThat(tf.failed()).isTrue();
      assertThat(tf.cause()).hasMessageContaining("Failed to get token");
    }
  }

  @Nested
  class WhenSystemUserNotRequired {

    @BeforeEach
    void setup() {
      var mb = new ModuleBootstrapDiscovery();
      mb.setSystemUserRequired(false);

      service.onModuleBootstrap(mb, ModuleBootstrapListener.ChangeType.INIT);
    }

    @Test
    void syncCache_positive_withTokenCacheNotAffected() {
      service.syncCache(EntitlementsEvent.of(Set.of("tenant-foo", "tenant-baz", "tenant-qux")));

      verifyNoInteractions(tokenCache);
    }

    @Test
    void getToken_positive_withEmptyResultReturned() {
      var tf = service.getToken(rc);

      assertThat(tf.succeeded()).isTrue();
      assertThat(tf.result()).isEmpty();
      verifyNoInteractions(tokenCache);
    }
  }

  @Test
  void retrieveToken_positive() throws Exception {
    String username = MODULE_NAME;
    String tenant = TENANT_NAME;

    when(moduleProperties.getName()).thenReturn(username);
    when(credentialService.getUserCredentials(tenant, username)).thenReturn(succeededFuture(TEST_USER));
    when(credentialService.getLoginClientCredentials(tenant)).thenReturn(succeededFuture(LOGIN_CLIENT_CREDENTIALS));
    when(keycloakService.obtainUserToken(tenant, LOGIN_CLIENT_CREDENTIALS, TEST_USER))
      .thenReturn(succeededFuture(TOKEN_RESPONSE));

    var result = service.retrieveToken(tenant);

    assertThat(result).isEqualTo(TOKEN_RESPONSE);
  }

  @Test
  void retrieveToken_positive_recoveredFromUnauthorizedUser() throws Exception {
    String username = MODULE_NAME;
    String tenant = TENANT_NAME;

    when(moduleProperties.getName()).thenReturn(username);
    when(credentialService.getUserCredentials(tenant, username))
      .thenReturn(failedFuture(new UnauthorizedException("User is unauthorized")))
      .thenReturn(succeededFuture(TEST_USER));
    when(credentialService.getLoginClientCredentials(tenant)).thenReturn(succeededFuture(LOGIN_CLIENT_CREDENTIALS));
    when(keycloakService.obtainUserToken(tenant, LOGIN_CLIENT_CREDENTIALS, TEST_USER))
      .thenReturn(succeededFuture(TOKEN_RESPONSE));

    var result = service.retrieveToken(tenant);

    assertThat(result).isEqualTo(TOKEN_RESPONSE);

    verify(credentialService).resetUserCredentials(tenant, username);
    verify(credentialService).resetLoginClientCredentials(tenant);
  }

  @Test
  void retrieveToken_positive_recoveredFromUnauthorizedLoginClient() throws Exception {
    String username = MODULE_NAME;
    String tenant = TENANT_NAME;

    when(moduleProperties.getName()).thenReturn(username);
    when(credentialService.getUserCredentials(tenant, username)).thenReturn(succeededFuture(TEST_USER));
    when(credentialService.getLoginClientCredentials(tenant))
      .thenReturn(failedFuture(new UnauthorizedException("Login client is unauthorized")))
      .thenReturn(succeededFuture(LOGIN_CLIENT_CREDENTIALS));
    when(keycloakService.obtainUserToken(tenant, LOGIN_CLIENT_CREDENTIALS, TEST_USER))
      .thenReturn(succeededFuture(TOKEN_RESPONSE));

    var result = service.retrieveToken(tenant);

    assertThat(result).isEqualTo(TOKEN_RESPONSE);

    verify(credentialService).resetUserCredentials(tenant, username);
    verify(credentialService).resetLoginClientCredentials(tenant);
  }

  @Test
  void retrieveToken_negative_authenticationFailed() {
    String username = MODULE_NAME;
    String tenant = TENANT_NAME;

    when(moduleProperties.getName()).thenReturn(username);
    when(credentialService.getUserCredentials(tenant, username)).thenReturn(succeededFuture(TEST_USER));
    when(credentialService.getLoginClientCredentials(tenant)).thenReturn(succeededFuture(LOGIN_CLIENT_CREDENTIALS));
    when(keycloakService.obtainUserToken(tenant, LOGIN_CLIENT_CREDENTIALS, TEST_USER))
      .thenReturn(failedFuture(new AuthenticationFailedException("Authentication error")));

    assertThatThrownBy(() -> service.retrieveToken(tenant))
      .isInstanceOf(ExecutionException.class)
      .hasMessageContaining("Authentication error")
      .cause().isInstanceOf(AuthenticationFailedException.class);
  }

  @Test
  void retrieveToken_negative_getUserCredentialsFailed() {
    String username = MODULE_NAME;
    String tenant = TENANT_NAME;

    when(moduleProperties.getName()).thenReturn(username);
    when(credentialService.getUserCredentials(tenant, username))
      .thenReturn(failedFuture(new RuntimeException("User credentials error")));
    when(credentialService.getLoginClientCredentials(tenant)).thenReturn(succeededFuture(LOGIN_CLIENT_CREDENTIALS));

    assertThatThrownBy(() -> service.retrieveToken(tenant))
      .isInstanceOf(ExecutionException.class)
      .hasMessageContaining("User credentials error")
      .cause().isInstanceOf(RuntimeException.class);
  }

  @Test
  void retrieveToken_negative_getLoginClientCredentialsFailed() {
    String username = MODULE_NAME;
    String tenant = TENANT_NAME;

    when(moduleProperties.getName()).thenReturn(username);
    when(credentialService.getUserCredentials(tenant, username)).thenReturn(succeededFuture(TEST_USER));
    when(credentialService.getLoginClientCredentials(tenant))
      .thenReturn(failedFuture(new RuntimeException("Login client credentials error")));

    assertThatThrownBy(() -> service.retrieveToken(tenant))
      .isInstanceOf(ExecutionException.class)
      .hasMessageContaining("Login client credentials error")
      .cause().isInstanceOf(RuntimeException.class);
  }

  @Test
  void onModuleBootstrap_positive_cacheInvalidated_whenSystemUserChangedToFalse() {
    var mb = new ModuleBootstrapDiscovery();
    mb.setSystemUserRequired(true);

    service.onModuleBootstrap(mb, ModuleBootstrapListener.ChangeType.INIT);

    verifyNoInteractions(tokenCache); // initially, nothing should happen with token cache

    var loadingCache = mock(LoadingCache.class);
    when(tokenCache.synchronous()).thenReturn(loadingCache);

    mb.setSystemUserRequired(false);
    service.onModuleBootstrap(mb, ModuleBootstrapListener.ChangeType.UPDATE);

    verify(loadingCache).invalidateAll();
  }

  private void mockRequest() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TENANT_NAME);
    when(request.getHeader(OkapiHeaders.REQUEST_ID)).thenReturn(REQUEST_ID);
    when(request.uri()).thenReturn("/foo/entities");
    when(request.method()).thenReturn(GET);
  }
}
