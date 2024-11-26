package org.folio.sidecar.service.token;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Map.entry;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.UserCredentials;
import org.folio.sidecar.service.store.AsyncSecureStore;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.utils.SecureStoreUtils;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
@Disabled("Needs adoption to async cache")
class SystemUserTokenProviderTest {

  private static final UserCredentials TEST_USER = UserCredentials.of(TestConstants.MODULE_NAME, "testpwd");
  private static final String SYS_USER_STORE_KEY =
    SecureStoreUtils.tenantStoreKey(TestConstants.TENANT_NAME, TestConstants.MODULE_NAME);

  @Mock private KeycloakService keycloakService;
  @Mock private AsyncSecureStore secureStore;
  @Mock private KeycloakProperties keycloakProperties;
  @Mock private ModuleProperties moduleProperties;
  @Mock private AsyncTokenCacheFactory cacheFactory;
  @Mock private RoutingContext rc;
  @Mock private HttpServerRequest request;

  @Mock private AsyncLoadingCache<String, TokenResponse> tokenCache;
  private SystemUserTokenProvider service;

  @BeforeEach
  void setup() {
    when(cacheFactory.createCache(ArgumentMatchers.any())).thenReturn(tokenCache);
    service = new SystemUserTokenProvider(keycloakService, keycloakProperties, moduleProperties,
      cacheFactory, secureStore);
  }

  @Test
  void syncCache_positive() {
    var cached = Map.ofEntries(
      entry("tenant-foo", completedFuture(new TokenResponse())),
      entry("tenant-xyz", completedFuture(new TokenResponse()))
    );
    when(tokenCache.asMap()).thenReturn(new ConcurrentHashMap<>(cached));

    when(keycloakService.obtainUserToken(any(), any(), any())).thenReturn(
      succeededFuture(TestConstants.TOKEN_RESPONSE));
    when(moduleProperties.getName()).thenReturn(TestConstants.MODULE_NAME);
    when(keycloakProperties.getLoginClientSuffix()).thenReturn("-login-app");
    when(secureStore.get(any())).thenReturn(succeededFuture("testpwd")).thenReturn(succeededFuture("secret"));

    service.syncCache(EntitlementsEvent.of(Set.of("tenant-foo", "tenant-bar")));

    // TODO (Dima Tkachenko): review code
    /*verify(tokenCache).invalidate("tenant-xyz");
    verify(tokenCache).put("tenant-bar", TestConstants.TOKEN_RESPONSE);*/
  }

  @Test
  void getToken_positive() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(keycloakService.obtainUserToken(any(), any(), any())).thenReturn(
      succeededFuture(TestConstants.TOKEN_RESPONSE));
    when(moduleProperties.getName()).thenReturn(TestConstants.MODULE_NAME);
    when(keycloakProperties.getLoginClientSuffix()).thenReturn("-login-app");
    when(secureStore.get(any())).thenReturn(succeededFuture("testpwd")).thenReturn(succeededFuture("secret"));

    var future = service.getToken(rc);

    assertTrue(future.succeeded());
    verify(keycloakService).obtainUserToken(TestConstants.TENANT_NAME, TestConstants.LOGIN_CLIENT_CREDENTIALS,
      TEST_USER);
    // TODO (Dima Tkachenko): review code
    /*verify(tokenCache).put(TestConstants.TENANT_NAME, TestConstants.TOKEN_RESPONSE);*/
  }

  @Test
  void getToken_positive_cachedValue() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    // TODO (Dima Tkachenko): review code
    /*when(tokenCache.getIfPresent(TestConstants.TENANT_NAME)).thenReturn(TestConstants.TOKEN_RESPONSE);*/

    var future = service.getToken(rc);

    assertTrue(future.succeeded());
    verifyNoInteractions(keycloakService);
    verifyNoInteractions(secureStore);
  }

  @Test
  void getToken_negative_sysUserSecretIsNotFound() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(moduleProperties.getName()).thenReturn(TestConstants.MODULE_NAME);
    when(secureStore.get(SYS_USER_STORE_KEY)).thenReturn(
      failedFuture("System user credentials are not found"));

    var future = service.getToken(rc);

    assertTrue(future.failed());
    assertThat(future.cause()).hasMessage("System user credentials are not found");
    verifyNoInteractions(keycloakService);
  }

  @Test
  void getToken_negative_kcError() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(keycloakService.obtainUserToken(any(), any(), any())).thenReturn(failedFuture("KC failure"));
    when(moduleProperties.getName()).thenReturn(TestConstants.MODULE_NAME);
    when(keycloakProperties.getLoginClientSuffix()).thenReturn("-login-app");
    when(secureStore.get(any())).thenReturn(succeededFuture("testpwd")).thenReturn(succeededFuture("secret"));

    var future = service.getToken(rc);

    assertTrue(future.failed());
    assertThat(future.cause()).hasMessage("KC failure");
  }

  @Test
  void getToken_negative_secureStoreError() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(moduleProperties.getName()).thenReturn(TestConstants.MODULE_NAME);
    when(keycloakProperties.getLoginClientSuffix()).thenReturn("-login-app");
    when(secureStore.get(any())).thenReturn(succeededFuture("testpwd"))
      .thenReturn(failedFuture("Secure store failure"));

    var future = service.getToken(rc);

    assertTrue(future.failed());
    assertThat(future.cause()).hasMessage("Secure store failure");
  }
}
