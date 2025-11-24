package org.folio.sidecar.integration.cred;

import static org.folio.sidecar.utils.SecureStoreUtils.globalStoreKey;
import static org.folio.sidecar.utils.SecureStoreUtils.tenantStoreKey;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.integration.cred.store.AsyncSecureStore;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.utils.SecureStoreUtils;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class CredentialService {

  private final KeycloakProperties properties;
  private final AsyncSecureStore secureStore;
  private final Cache<String, ClientCredentials> clientCredentialsCache;
  private final Cache<String, UserCredentials> userCredentialsCache;

  public Future<ClientCredentials> getAdminClientCredentials() {
    log.debug("Retrieving admin client credentials");

    return clientCredentials(properties::getAdminClientId,
      SecureStoreUtils::globalStoreKey,
      creds -> log.debug("Admin client credentials obtained: clientId = {}", creds.getClientId()));
  }

  public void resetAdminClientCredentials() {
    clientCredentialsCache.invalidate(globalStoreKey(properties.getAdminClientId()));
  }

  public Future<ClientCredentials> getServiceClientCredentials(String tenant) {
    log.debug("Retrieving service client credentials: tenant = {}", tenant);

    return clientCredentials(properties::getServiceClientId,
      clientId -> tenantStoreKey(tenant, clientId),
      creds -> log.debug("Service client credentials obtained: clientId = {}, tenant = {}", creds.getClientId(), tenant)
    );
  }

  public void resetServiceClientCredentials(String tenant) {
    clientCredentialsCache.invalidate(tenantStoreKey(tenant, properties.getServiceClientId()));
  }

  public Future<ClientCredentials> getLoginClientCredentials(String tenant) {
    log.debug("Retrieving login client credentials: tenant = {}", tenant);

    return clientCredentials(() -> tenant + properties.getLoginClientSuffix(),
      clientId -> tenantStoreKey(tenant, clientId),
      creds -> log.debug("Login client credentials obtained: clientId = {}, tenant = {}", creds.getClientId(), tenant));
  }

  public void resetLoginClientCredentials(String tenant) {
    clientCredentialsCache.invalidate(tenantStoreKey(tenant, tenant + properties.getLoginClientSuffix()));
  }

  public Future<ClientCredentials> getImpersonationClientCredentials(String tenant) {
    log.debug("Retrieving impersonation client credentials: tenant = {}", tenant);

    return clientCredentials(properties::getImpersonationClientId,
      clientId -> tenantStoreKey(tenant, clientId),
      creds -> log.debug("Impersonation client credentials obtained: clientId = {}, tenant = {}",
        creds.getClientId(), tenant));
  }

  public void resetImpersonationClientCredentials(String tenant) {
    clientCredentialsCache.invalidate(tenantStoreKey(tenant, properties.getImpersonationClientId()));
  }

  public Future<UserCredentials> getUserCredentials(String tenant, String username) {
    log.debug("Retrieving user credentials: tenant = {}, username = {}", tenant, username);

    String key = tenantStoreKey(tenant, username);

    return getCredsFromCacheOrSecureStore(
      userCredentialsCache, key,
      secret -> UserCredentials.of(username, secret)
    ).onSuccess(creds -> log.debug("User credentials obtained: username = {}, tenant = {}",
      creds.getUsername(), tenant));
  }

  public void resetUserCredentials(String tenant, String username) {
    userCredentialsCache.invalidate(tenantStoreKey(tenant, username));
  }

  private Future<ClientCredentials> clientCredentials(Supplier<String> clientIdSupplier,
    UnaryOperator<String> clientIdToKeyMapper, Handler<ClientCredentials> onSuccessHandler) {
    var clientId = clientIdSupplier.get();
    var key = clientIdToKeyMapper.apply(clientId);

    return getCredsFromCacheOrSecureStore(
      clientCredentialsCache, key,
      secret -> ClientCredentials.of(clientId, secret)
    ).onSuccess(onSuccessHandler);
  }

  private <T> Future<T> getCredsFromCacheOrSecureStore(Cache<String, T> cache, String cacheKey,
    Function<String, T> credentialsFactory) {
    var creds = cache.getIfPresent(cacheKey);
    if (creds != null) {
      log.debug("Credentials found in cache: key = {}", cacheKey);
      return Future.succeededFuture(creds);
    }

    return secureStore.get(cacheKey)
      .map(secret -> {
        var credentials = credentialsFactory.apply(secret);
        cache.put(cacheKey, credentials);
        log.debug("Credentials stored in cache: key = {}", cacheKey);

        return credentials;
      });
  }
}
