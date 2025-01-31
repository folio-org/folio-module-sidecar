package org.folio.sidecar.integration.cred;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.utils.SecureStoreUtils.globalStoreKey;
import static org.folio.sidecar.utils.SecureStoreUtils.tenantStoreKey;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.Future;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.integration.cred.store.AsyncSecureStore;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {

  private static final String ADMIN_CLIENT_ID = "folio-backend-admin";
  private static final String ADMIN_CLIENT_SECRET = "admin_secret";
  private static final ClientCredentials ADMIN_CLIENT_CREDENTIALS =
    ClientCredentials.of(ADMIN_CLIENT_ID, ADMIN_CLIENT_SECRET);
  private static final String SERVICE_CLIENT_ID = "sidecar-module-access-client";
  private static final String SERVICE_CLIENT_SECRET = "service_secret";
  private static final ClientCredentials SERVICE_CLIENT_CREDENTIALS =
    ClientCredentials.of(SERVICE_CLIENT_ID, SERVICE_CLIENT_SECRET);
  private static final String LOGIN_SUFFIX = "-login-application";
  private static final String LOGIN_CLIENT_ID = TENANT_NAME + LOGIN_SUFFIX;
  private static final String LOGIN_CLIENT_SECRET = "login_secret";
  private static final ClientCredentials LOGIN_CLIENT_CREDENTIALS =
    ClientCredentials.of(LOGIN_CLIENT_ID, LOGIN_CLIENT_SECRET);
  private static final String IMPERSONATION_CLIENT_ID = "impersonation-client";
  private static final String IMPERSONATION_CLIENT_SECRET = "impersonation-secret";
  private static final ClientCredentials IMPERSONATION_CLIENT_CREDENTIALS =
    ClientCredentials.of(IMPERSONATION_CLIENT_ID, IMPERSONATION_CLIENT_SECRET);
  private static final String USERNAME = "test-user";
  private static final String USER_SECRET = "user_secret";
  private static final UserCredentials USER_CREDENTIALS = UserCredentials.of(USERNAME, USER_SECRET);

  @Mock private KeycloakProperties properties;
  @Mock private AsyncSecureStore secureStore;
  @Mock private Cache<String, ClientCredentials> clientCredentialsCache;
  @Mock private Cache<String, UserCredentials> userCredentialsCache;

  private CredentialService credentialService;

  @BeforeEach
  void setUp() {
    credentialService = new CredentialService(properties, secureStore, clientCredentialsCache, userCredentialsCache);
  }

  @Test
  void getAdminClientCredentials_positive_cachedValue() {
    when(properties.getAdminClientId()).thenReturn(ADMIN_CLIENT_ID);
    var cacheKey = globalStoreKey(ADMIN_CLIENT_ID);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(ADMIN_CLIENT_CREDENTIALS);

    var result = credentialService.getAdminClientCredentials();

    assertExpectedCredential(result, ADMIN_CLIENT_CREDENTIALS);
    verifyNoInteractions(secureStore);
  }

  @Test
  void getAdminClientCredentials_positive_fromSecureStore() {
    when(properties.getAdminClientId()).thenReturn(ADMIN_CLIENT_ID);
    var cacheKey = globalStoreKey(ADMIN_CLIENT_ID);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(secureStore.get(cacheKey)).thenReturn(succeededFuture(ADMIN_CLIENT_SECRET));

    var result = credentialService.getAdminClientCredentials();

    assertExpectedCredential(result, ADMIN_CLIENT_CREDENTIALS);
    verify(clientCredentialsCache).put(cacheKey, ADMIN_CLIENT_CREDENTIALS);
  }

  @Test
  void getServiceClientCredentials_positive_cachedValue() {
    String cacheKey = tenantStoreKey(TENANT_NAME, SERVICE_CLIENT_ID);
    when(properties.getServiceClientId()).thenReturn(SERVICE_CLIENT_ID);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(SERVICE_CLIENT_CREDENTIALS);

    var result = credentialService.getServiceClientCredentials(TENANT_NAME);

    assertExpectedCredential(result, SERVICE_CLIENT_CREDENTIALS);
    verifyNoInteractions(secureStore);
  }

  @Test
  void getServiceClientCredentials_positive_fromSecureStore() {
    String cacheKey = tenantStoreKey(TENANT_NAME, SERVICE_CLIENT_ID);
    when(properties.getServiceClientId()).thenReturn(SERVICE_CLIENT_ID);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(secureStore.get(cacheKey)).thenReturn(succeededFuture(SERVICE_CLIENT_SECRET));

    var result = credentialService.getServiceClientCredentials(TENANT_NAME);

    assertExpectedCredential(result, SERVICE_CLIENT_CREDENTIALS);
    verify(clientCredentialsCache).put(cacheKey, SERVICE_CLIENT_CREDENTIALS);
  }

  @Test
  void getLoginClientCredentials_positive_cachedValue() {
    String cacheKey = tenantStoreKey(TENANT_NAME, LOGIN_CLIENT_ID);
    when(properties.getLoginClientSuffix()).thenReturn(LOGIN_SUFFIX);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(LOGIN_CLIENT_CREDENTIALS);

    var result = credentialService.getLoginClientCredentials(TENANT_NAME);

    assertExpectedCredential(result, LOGIN_CLIENT_CREDENTIALS);
    verifyNoInteractions(secureStore);
  }

  @Test
  void getLoginClientCredentials_positive_fromSecureStore() {
    String cacheKey = tenantStoreKey(TENANT_NAME, LOGIN_CLIENT_ID);
    when(properties.getLoginClientSuffix()).thenReturn(LOGIN_SUFFIX);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(secureStore.get(cacheKey)).thenReturn(succeededFuture(LOGIN_CLIENT_SECRET));

    var result = credentialService.getLoginClientCredentials(TENANT_NAME);

    assertExpectedCredential(result, LOGIN_CLIENT_CREDENTIALS);
    verify(clientCredentialsCache).put(cacheKey, LOGIN_CLIENT_CREDENTIALS);
  }

  @Test
  void getImpersonationClientCredentials_positive_cachedValue() {
    String cacheKey = tenantStoreKey(TENANT_NAME, IMPERSONATION_CLIENT_ID);
    when(properties.getImpersonationClientId()).thenReturn(IMPERSONATION_CLIENT_ID);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(IMPERSONATION_CLIENT_CREDENTIALS);

    var result = credentialService.getImpersonationClientCredentials(TENANT_NAME);

    assertExpectedCredential(result, IMPERSONATION_CLIENT_CREDENTIALS);
    verifyNoInteractions(secureStore);
  }

  @Test
  void getImpersonationClientCredentials_positive_fromSecureStore() {
    String cacheKey = tenantStoreKey(TENANT_NAME, IMPERSONATION_CLIENT_ID);
    when(properties.getImpersonationClientId()).thenReturn(IMPERSONATION_CLIENT_ID);
    when(clientCredentialsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(secureStore.get(cacheKey)).thenReturn(succeededFuture(IMPERSONATION_CLIENT_SECRET));

    var result = credentialService.getImpersonationClientCredentials(TENANT_NAME);

    assertExpectedCredential(result, IMPERSONATION_CLIENT_CREDENTIALS);
    verify(clientCredentialsCache).put(cacheKey, IMPERSONATION_CLIENT_CREDENTIALS);
  }

  @Test
  void getUserCredentials_positive_cachedValue() {
    String cacheKey = tenantStoreKey(TENANT_NAME, USERNAME);
    when(userCredentialsCache.getIfPresent(cacheKey)).thenReturn(USER_CREDENTIALS);

    var result = credentialService.getUserCredentials(TENANT_NAME, USERNAME);

    assertExpectedUserCredential(result, USER_CREDENTIALS);
    verifyNoInteractions(secureStore);
  }

  @Test
  void getUserCredentials_positive_fromSecureStore() {
    String cacheKey = tenantStoreKey(TENANT_NAME, USERNAME);
    when(userCredentialsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(secureStore.get(cacheKey)).thenReturn(succeededFuture(USER_SECRET));

    var result = credentialService.getUserCredentials(TENANT_NAME, USERNAME);

    assertExpectedUserCredential(result, USER_CREDENTIALS);
    verify(userCredentialsCache).put(cacheKey, USER_CREDENTIALS);
  }

  @Test
  void resetAdminClientCredentials_positive() {
    when(properties.getAdminClientId()).thenReturn(ADMIN_CLIENT_ID);

    credentialService.resetAdminClientCredentials();

    verify(clientCredentialsCache).invalidate(globalStoreKey(ADMIN_CLIENT_ID));
  }

  @Test
  void resetServiceClientCredentials_positive() {
    when(properties.getServiceClientId()).thenReturn(SERVICE_CLIENT_ID);

    credentialService.resetServiceClientCredentials(TENANT_NAME);

    verify(clientCredentialsCache).invalidate(tenantStoreKey(TENANT_NAME, SERVICE_CLIENT_ID));
  }

  @Test
  void resetLoginClientCredentials_positive() {
    when(properties.getLoginClientSuffix()).thenReturn(LOGIN_SUFFIX);

    credentialService.resetLoginClientCredentials(TENANT_NAME);

    verify(clientCredentialsCache).invalidate(tenantStoreKey(TENANT_NAME, TENANT_NAME + LOGIN_SUFFIX));
  }

  @Test
  void resetImpersonationClientCredentials_positive() {
    when(properties.getImpersonationClientId()).thenReturn(IMPERSONATION_CLIENT_ID);

    credentialService.resetImpersonationClientCredentials(TENANT_NAME);

    verify(clientCredentialsCache).invalidate(tenantStoreKey(TENANT_NAME, IMPERSONATION_CLIENT_ID));
  }

  @Test
  void resetUserCredentials_positive() {
    credentialService.resetUserCredentials(TENANT_NAME, USERNAME);

    verify(userCredentialsCache).invalidate(tenantStoreKey(TENANT_NAME, USERNAME));
  }

  private static void assertExpectedCredential(Future<ClientCredentials> result, ClientCredentials expected) {
    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(expected);
  }

  private static void assertExpectedUserCredential(Future<UserCredentials> result, UserCredentials expected) {
    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(expected);
  }
}
