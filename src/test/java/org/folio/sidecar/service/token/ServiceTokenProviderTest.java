package org.folio.sidecar.service.token;

import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
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
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.support.TestConstants;
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
  private static final String SERVICE_CLIENT_SECRET = "service_secret";
  private static final String ADMIN_CLIENT_ID = "folio-backend-admin";
  private static final String ADMIN_CLIENT_SECRET = "admin_secret";

  @Mock private KeycloakService keycloakService;
  @Mock private RoutingContext rc;
  @Mock private HttpServerRequest request;
  @Mock private CredentialService credentialService;
  @Mock private AsyncTokenCacheFactory cacheFactory;
  @Mock private AsyncLoadingCache<String, TokenResponse> serviceTokenCache;

  private ServiceTokenProvider service;

  @BeforeEach
  void setup() {
    when(cacheFactory.createCache(ArgumentMatchers.any())).thenReturn(serviceTokenCache);
    service = new ServiceTokenProvider(keycloakService, credentialService, cacheFactory);
  }

  @Test
  void syncCache_positive() {
    var map = Map.of(
      "another-tenant", completedFuture(new TokenResponse()),
      TENANT_NAME, completedFuture(new TokenResponse()));
    when(serviceTokenCache.asMap()).thenReturn(new ConcurrentHashMap<>(map));

    service.syncCache(EntitlementsEvent.of(Set.of(TENANT_NAME)));
  }

  @Test
  void getAdminToken_positive() {
    var clientCredentials = ClientCredentials.of(ADMIN_CLIENT_ID, ADMIN_CLIENT_SECRET);

    when(credentialService.getAdminClientCredentials()).thenReturn(succeededFuture(clientCredentials));
    when(keycloakService.obtainToken(any(), any())).thenReturn(succeededFuture(TestConstants.TOKEN_RESPONSE));

    var future = service.getAdminToken();

    assertTrue(future.succeeded());
    verify(keycloakService).obtainToken("master", clientCredentials);
  }

  @Test
  void getServiceToken_positive() {
    var clientCredentials = ClientCredentials.of(SERVICE_CLIENT_ID, SERVICE_CLIENT_SECRET);

    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TENANT_NAME);
    when(credentialService.getServiceClientCredentials(TENANT_NAME)).thenReturn(succeededFuture(clientCredentials));
    when(keycloakService.obtainToken(any(), any(), any())).thenReturn(succeededFuture(TestConstants.TOKEN_RESPONSE));

    var future = service.getToken(rc);

    assertTrue(future.succeeded());
    verify(keycloakService).obtainToken(
      TENANT_NAME, clientCredentials, rc);
  }

  @Test
  void getServiceToken_positive_cachedToken() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TENANT_NAME);
    when(serviceTokenCache.get(any())).thenReturn(completedFuture(TestConstants.TOKEN_RESPONSE));

    var future = service.getToken(rc);

    assertTrue(future.succeeded());
    verifyNoInteractions(keycloakService);
  }
}
