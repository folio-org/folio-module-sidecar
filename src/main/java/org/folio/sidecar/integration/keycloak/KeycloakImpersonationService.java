package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT;
import static org.folio.sidecar.utils.SecureStoreUtils.tenantStoreKey;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ClientErrorException;
import java.util.Map.Entry;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.users.model.User;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.service.CacheInvalidatable;
import org.folio.sidecar.service.token.TokenCacheFactory;
import org.folio.sidecar.service.store.AsyncSecureStore;

@Log4j2
@ApplicationScoped
public class KeycloakImpersonationService implements CacheInvalidatable {

  private final KeycloakClient keycloakClient;
  private final AsyncSecureStore secureStore;
  private final Cache<String, TokenResponse> tokenCache;
  private final KeycloakProperties properties;

  public KeycloakImpersonationService(KeycloakClient keycloakClient, AsyncSecureStore secureStore,
    TokenCacheFactory cacheFactory, KeycloakProperties sidecarProperties) {
    this.keycloakClient = keycloakClient;
    this.secureStore = secureStore;
    this.tokenCache = cacheFactory.createCache();
    this.properties = sidecarProperties;
  }

  public Future<TokenResponse> getUserToken(String targetTenant, User user) {
    var key = buildCacheKey(targetTenant, user.getId());
    var userToken = tokenCache.getIfPresent(key);
    if (userToken != null) {
      log.debug("User token found in cache: username = {}, targetTenant = {}", user.getUsername(), targetTenant);
      return succeededFuture(userToken);
    }

    var impersonationClient = properties.getImpersonationClient();
    return secureStore.get(tenantStoreKey(targetTenant, impersonationClient))
      .map(secret -> ClientCredentials.of(impersonationClient, secret))
      .flatMap(credentials -> keycloakClient.impersonateUserToken(targetTenant, credentials, user.getUsername()))
      .flatMap(tokenResponse -> {
        var statusCode = tokenResponse.statusCode();
        if (statusCode != 200) {
          log.error("Failed to impersonate user token: response = {}", tokenResponse.bodyAsString());
          var exception = new ClientErrorException("Failed to impersonate user token", statusCode);
          return failedFuture(exception);
        }
        var token = tokenResponse.bodyAsJson(TokenResponse.class);
        tokenCache.put(key, token);

        log.debug("User token impersonated and cached: username = {}, targetTenant = {}", user.getUsername(),
          targetTenant);
        return succeededFuture(token);
      });
  }

  @Override
  public void invalidate(LogoutEvent event) {
    tokenCache.asMap().entrySet().removeIf(entry -> shouldRemove(event, entry));
  }

  private static boolean shouldRemove(LogoutEvent event, Entry<String, TokenResponse> entry) {
    if (LOGOUT == event.getType()) {
      return false;
    }
    if (entry.getKey().contains(event.getUserId())) {
      log.info("Invalidating impersonated user token cache: key = {}", entry.getKey());
      return true;
    }
    return false;
  }

  private static String buildCacheKey(String tenant, String userId) {
    return tenant + ":" + userId;
  }
}
