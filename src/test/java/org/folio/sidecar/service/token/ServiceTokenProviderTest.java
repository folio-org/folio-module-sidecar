package org.folio.sidecar.service.token;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.util.Collections.emptyMap;
import static java.util.Map.entry;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.sidecar.support.TestConstants.REQUEST_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TOKEN_RESPONSE;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.folio.sidecar.configuration.properties.TokenCacheProperties;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ServiceTokenProviderTest {

  private static final String MASTER_TENANT = "master";

  private static final String SERVICE_CLIENT_ID = "sidecar-module-access-client";
  private static final String SERVICE_CLIENT_SECRET = "service_secret";
  private static final ClientCredentials SERVICE_CLIENT_CREDENTIALS =
    ClientCredentials.of(SERVICE_CLIENT_ID, SERVICE_CLIENT_SECRET);

  private static final String ADMIN_CLIENT_ID = "folio-backend-admin";
  private static final String ADMIN_CLIENT_SECRET = "admin_secret";
  private static final ClientCredentials ADMIN_CLIENT_CREDENTIALS =
    ClientCredentials.of(ADMIN_CLIENT_ID, ADMIN_CLIENT_SECRET);

  @Mock private KeycloakService keycloakService;
  @Mock private RoutingContext rc;
  @Mock private HttpServerRequest request;
  @Mock private CredentialService credentialService;
  @Mock private AsyncTokenCacheFactory cacheFactory;
  @Mock private AsyncLoadingCache<String, TokenResponse> tokenCache;
  @Mock private TokenCacheProperties cacheProperties;

  private ServiceTokenProvider service;

  @BeforeEach
  void setup() {
    when(cacheFactory.createCache(ArgumentMatchers.any())).thenReturn(tokenCache);
    when(cacheProperties.getRetrievalTimeoutSeconds()).thenReturn(30);
    service = new ServiceTokenProvider(keycloakService, credentialService, cacheFactory, cacheProperties);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(keycloakService, credentialService, tokenCache);
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
  void getAdminToken_positive() {
    when(tokenCache.get("master")).thenReturn(completedFuture(TOKEN_RESPONSE));

    var tf = service.getAdminToken();

    assertThat(tf.succeeded()).isTrue();
    assertThat(tf.result()).isEqualTo(TOKEN_RESPONSE.getAccessToken());
  }

  @Test
  void getToken_positive() {
    mockRequest();
    when(tokenCache.get(TENANT_NAME)).thenReturn(completedFuture(TOKEN_RESPONSE));

    var tf = service.getToken(rc);

    assertThat(tf.succeeded()).isTrue();
    assertThat(tf.result()).isEqualTo(TOKEN_RESPONSE.getAccessToken());
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

  @Test
  void retrieveToken_positive_adminClient() throws Exception {
    when(credentialService.getAdminClientCredentials()).thenReturn(succeededFuture(ADMIN_CLIENT_CREDENTIALS));
    when(keycloakService.obtainToken(MASTER_TENANT, ADMIN_CLIENT_CREDENTIALS))
      .thenReturn(succeededFuture(TOKEN_RESPONSE));

    var result = service.retrieveToken(MASTER_TENANT);

    assertThat(result).isEqualTo(TOKEN_RESPONSE);
  }

  @Test
  void retrieveToken_positive_serviceClient() throws Exception {
    when(credentialService.getServiceClientCredentials(TENANT_NAME))
      .thenReturn(succeededFuture(SERVICE_CLIENT_CREDENTIALS));
    when(keycloakService.obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS))
      .thenReturn(succeededFuture(TOKEN_RESPONSE));

    var result = service.retrieveToken(TENANT_NAME);

    assertThat(result).isEqualTo(TOKEN_RESPONSE);
  }

  @Test
  void retrieveToken_positive_recoveredFromUnauthorizedServiceClient() throws Exception {
    when(credentialService.getServiceClientCredentials(TENANT_NAME))
      .thenReturn(failedFuture(new UnauthorizedException("Service client is unauthorized")))
      .thenReturn(succeededFuture(SERVICE_CLIENT_CREDENTIALS));
    when(keycloakService.obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS))
      .thenReturn(succeededFuture(TOKEN_RESPONSE));

    var result = service.retrieveToken(TENANT_NAME);

    assertThat(result).isEqualTo(TOKEN_RESPONSE);

    verify(credentialService).resetServiceClientCredentials(TENANT_NAME);
  }

  @Test
  void retrieveToken_positive_recoveredFromUnauthorizedAdminClient() throws Exception {
    when(credentialService.getAdminClientCredentials())
      .thenReturn(failedFuture(new UnauthorizedException("Admin client is unauthorized")))
      .thenReturn(succeededFuture(ADMIN_CLIENT_CREDENTIALS));
    when(keycloakService.obtainToken(MASTER_TENANT, ADMIN_CLIENT_CREDENTIALS))
      .thenReturn(succeededFuture(TOKEN_RESPONSE));

    var result = service.retrieveToken(MASTER_TENANT);

    assertThat(result).isEqualTo(TOKEN_RESPONSE);

    verify(credentialService).resetAdminClientCredentials();
  }

  @Test
  void retrieveToken_negative_authenticationFailed() {
    when(credentialService.getServiceClientCredentials(TENANT_NAME))
      .thenReturn(succeededFuture(SERVICE_CLIENT_CREDENTIALS));
    when(keycloakService.obtainToken(TENANT_NAME, SERVICE_CLIENT_CREDENTIALS))
      .thenReturn(failedFuture(new AuthenticationFailedException("Authentication error")));

    assertThatThrownBy(() -> service.retrieveToken(TENANT_NAME))
      .isInstanceOf(ExecutionException.class)
      .hasMessageContaining("Authentication error")
      .cause().isInstanceOf(AuthenticationFailedException.class);
  }

  @Test
  void retrieveToken_negative_getServiceClientCredentialsFailed() {
    when(credentialService.getServiceClientCredentials(TENANT_NAME))
      .thenReturn(failedFuture(new RuntimeException("Service client credentials error")));

    assertThatThrownBy(() -> service.retrieveToken(TENANT_NAME))
      .isInstanceOf(ExecutionException.class)
      .hasMessageContaining("Service client credentials error")
      .cause().isInstanceOf(RuntimeException.class);
  }

  @Test
  void retrieveToken_negative_getAdminClientCredentialsFailed() {
    when(credentialService.getAdminClientCredentials())
      .thenReturn(failedFuture(new RuntimeException("Admin client credentials error")));

    assertThatThrownBy(() -> service.retrieveToken(MASTER_TENANT))
      .isInstanceOf(ExecutionException.class)
      .hasMessageContaining("Admin client credentials error")
      .cause().isInstanceOf(RuntimeException.class);
  }

  private void mockRequest() {
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TENANT_NAME);
    when(request.getHeader(OkapiHeaders.REQUEST_ID)).thenReturn(REQUEST_ID);
    when(request.uri()).thenReturn("/foo/entities");
    when(request.method()).thenReturn(GET);
  }
}
