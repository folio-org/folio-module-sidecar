package org.folio.sidecar.service.token;

import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
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
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.integration.cred.store.AsyncSecureStore;
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
class ServiceTokenProviderTest {

  private static final String SERVICE_CLIENT_ID = "sidecar-module-access-client";
  private static final String SERVICE_CLIENT_STORE_KEY =
    SecureStoreUtils.tenantStoreKey(TestConstants.TENANT_NAME, SERVICE_CLIENT_ID);
  private static final String SERVICE_CLIENT_SECRET = "service_secret";
  private static final String ADMIN_CLIENT_ID = "folio-backend-admin";
  private static final String ADMIN_CLIENT_STORE_KEY =
    SecureStoreUtils.tenantStoreKey(SecureStoreUtils.GLOBAL_SECTION, ADMIN_CLIENT_ID);
  private static final String ADMIN_CLIENT_SECRET = "admin_secret";

  @Mock private KeycloakProperties properties;
  @Mock private KeycloakService keycloakService;
  @Mock private RoutingContext rc;
  @Mock private HttpServerRequest request;
  @Mock private AsyncSecureStore secureStore;
  @Mock private AsyncTokenCacheFactory cacheFactory;
  @Mock private AsyncLoadingCache<String, TokenResponse> serviceTokenCache;

  private ServiceTokenProvider service;

  @BeforeEach
  void setup() {
    when(cacheFactory.createCache(ArgumentMatchers.any())).thenReturn(serviceTokenCache);
    service = new ServiceTokenProvider(keycloakService, properties, secureStore, cacheFactory);
  }

  @Test
  void syncCache_positive() {
    var map = Map.of(
      "another-tenant", completedFuture(new TokenResponse()),
      TestConstants.TENANT_NAME, completedFuture(new TokenResponse()));
    when(serviceTokenCache.asMap()).thenReturn(new ConcurrentHashMap<>(map));

    service.syncCache(EntitlementsEvent.of(Set.of(TestConstants.TENANT_NAME)));
  }

  @Test
  void getAdminToken_positive() {
    when(properties.getAdminClientId()).thenReturn(ADMIN_CLIENT_ID);
    when(secureStore.get(ADMIN_CLIENT_STORE_KEY)).thenReturn(succeededFuture(ADMIN_CLIENT_SECRET));
    when(keycloakService.obtainToken(any(), any())).thenReturn(succeededFuture(TestConstants.TOKEN_RESPONSE));

    var future = service.getAdminToken();

    assertTrue(future.succeeded());
    verify(keycloakService).obtainToken("master", ClientCredentials.of(ADMIN_CLIENT_ID, ADMIN_CLIENT_SECRET));
  }

  @Test
  void getServiceToken_positive() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(properties.getServiceClientId()).thenReturn(SERVICE_CLIENT_ID);
    when(secureStore.get(SERVICE_CLIENT_STORE_KEY)).thenReturn(succeededFuture(SERVICE_CLIENT_SECRET));
    when(keycloakService.obtainToken(any(), any(), any())).thenReturn(succeededFuture(TestConstants.TOKEN_RESPONSE));

    var future = service.getToken(rc);

    assertTrue(future.succeeded());
    verify(keycloakService).obtainToken(
      TestConstants.TENANT_NAME, ClientCredentials.of(SERVICE_CLIENT_ID, SERVICE_CLIENT_SECRET), rc);
  }

  @Test
  void getServiceToken_positive_cachedToken() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(serviceTokenCache.get(any())).thenReturn(completedFuture(TestConstants.TOKEN_RESPONSE));

    var future = service.getToken(rc);

    assertTrue(future.succeeded());
    verifyNoInteractions(keycloakService);
  }
}
