package org.folio.sidecar.service.token;

import static org.folio.sidecar.utils.SecureStoreUtils.tenantStoreKey;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.model.UserCredentials;
import org.folio.sidecar.service.store.AsyncSecureStore;
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
    log.info("Retrieving admin client credentials");

    return clientCredentials(properties::getAdminClientId,
      SecureStoreUtils::globalStoreKey,
      creds -> log.info("Admin client credentials obtained: clientId = {}", creds.getClientId()));
  }

  public Future<ClientCredentials> getServiceClientCredentials(String tenant) {
    log.info("Retrieving service client credentials: tenant = {}", tenant);

    return clientCredentials(properties::getServiceClientId,
      clientId -> tenantStoreKey(tenant, clientId),
      creds -> log.info("Service client credentials obtained: clientId = {}, tenant = {}", creds.getClientId(), tenant)
    );
  }

  public Future<ClientCredentials> getLoginClientCredentials(String tenant) {
    log.info("Retrieving login client credentials: tenant = {}", tenant);

    return clientCredentials(() -> tenant + properties.getLoginClientSuffix(),
      clientId -> tenantStoreKey(tenant, clientId),
      creds -> log.info("Login client credentials obtained: clientId = {}, tenant = {}", creds.getClientId(), tenant));
  }

  public Future<UserCredentials> getUserCredentials(String tenant, String username) {
    log.info("Retrieving user credentials: tenant = {}, username = {}", tenant, username);

    String key = tenantStoreKey(tenant, username);

    return getCredsFromCacheOrSecureStore(
      userCredentialsCache, key,
      secret -> UserCredentials.of(username, secret)
    ).onSuccess(creds -> log.info("User credentials obtained: username = {}, tenant = {}", creds.getUsername(), tenant));
  }

  private Future<ClientCredentials> clientCredentials(Supplier<String> clientIdSupplier,
    Function<String, String> clientIdToKeyMapper, Handler<ClientCredentials> onSuccessHandler) {
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
