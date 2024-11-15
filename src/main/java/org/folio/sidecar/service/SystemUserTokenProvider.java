package org.folio.sidecar.service;

import com.github.benmanes.caffeine.cache.Cache;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.UserCredentials;
import org.folio.sidecar.service.store.AsyncSecureStore;
import org.folio.sidecar.utils.CollectionUtils;
import org.folio.sidecar.utils.SecureStoreUtils;

@Log4j2
@ApplicationScoped
public class SystemUserTokenProvider {

  private final KeycloakService keycloakService;
  private final AsyncSecureStore secureStore;
  private final KeycloakProperties keycloakProperties;
  private final ModuleProperties moduleProperties;
  private final Cache<String, TokenResponse> tokenCache;

  @Inject
  SystemUserTokenProvider(KeycloakService keycloakService, KeycloakProperties properties,
    ModuleProperties moduleProperties, TokenCacheFactory cacheFactory, AsyncSecureStore secureStore) {
    this.keycloakService = keycloakService;
    this.keycloakProperties = properties;
    this.secureStore = secureStore;
    this.moduleProperties = moduleProperties;
    this.tokenCache = cacheFactory.createCache(this::refreshAndCacheToken);
  }

  @SuppressWarnings("unused")
  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void syncCache(EntitlementsEvent event) {
    var tenants = event.getTenants();
    syncTenantCache(tenants);
  }

  /**
   * Obtains a system user token.
   *
   * @param tenant tenant name
   * @return {@link Future} containing the access token.
   */
  public Future<String> getToken(String tenant) {
    var cachedValue = tokenCache.getIfPresent(tenant);
    if (cachedValue != null) {
      return Future.succeededFuture(cachedValue.getAccessToken());
    }
    return obtainAndCacheToken(tenant).map(TokenResponse::getAccessToken);
  }

  private Future<TokenResponse> obtainAndCacheToken(String tenant) {
    try {
      var username = moduleProperties.getName();
      return getUserCredentials(tenant, username)
        .compose(user -> obtainAndCacheToken(tenant, user, username));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private Future<TokenResponse> obtainAndCacheToken(String tenant, UserCredentials user, String username) {
    return getClientCredentials(tenant)
      .compose(client -> authUser(tenant, user, username, client))
      .map(cacheToken(tenant))
      .onFailure(e -> log.warn("Cannot obtain token: message = {}", e.getMessage(), e));
  }

  private Future<TokenResponse> authUser(String tenant, UserCredentials user, String username,
    ClientCredentials client) {
    log.info("Authenticating system user {} on tenant: {}", username, tenant);
    return keycloakService.obtainUserToken(tenant, client, user);
  }

  private void refreshAndCacheToken(String tenant, TokenResponse token) {
    var username = moduleProperties.getName();
    var refreshToken = token.getRefreshToken();
    log.debug("Refreshing system user {} token on tenant: {}", username, tenant);
    getClientCredentials(tenant)
      .onSuccess(client -> refreshAndCacheToken(tenant, client, refreshToken));
  }

  private Future<TokenResponse> refreshAndCacheToken(String tenant, ClientCredentials client, String refreshToken) {
    return keycloakService.refreshUserToken(tenant, client, refreshToken)
      .map(cacheToken(tenant))
      .onFailure(e -> {
        log.warn("Failed to refresh system user token. Trying to obtain a new one...");
        obtainAndCacheToken(tenant);
      });
  }

  public void syncTenantCache(Set<String> tenants) {
    if (CollectionUtils.isNotEmpty(tenants)) {
      log.info("Synchronizing system users cache");
      var cachedTenants = tokenCache.asMap().keySet();
      cachedTenants.stream().filter(cached -> !tenants.contains(cached)).forEach(tokenCache::invalidate);
      tenants.stream().filter(t -> !cachedTenants.contains(t)).forEach(this::obtainAndCacheToken);
    }
  }

  private Function<TokenResponse, TokenResponse> cacheToken(String tenant) {
    return token -> {
      tokenCache.put(tenant, token);
      return token;
    };
  }

  private Future<ClientCredentials> getClientCredentials(String tenant) {
    var clientId = tenant + keycloakProperties.getLoginClientSuffix();
    return secureStore.get(SecureStoreUtils.tenantStoreKey(tenant, clientId))
      .map(secret -> ClientCredentials.of(clientId, secret))
      .onFailure(e -> log.warn("Failed to obtain client credentials for tenant {}", tenant, e));
  }

  private Future<UserCredentials> getUserCredentials(String tenant, String username) {
    return secureStore.get(SecureStoreUtils.tenantStoreKey(tenant, username))
      .map(password -> UserCredentials.of(username, password));
  }
}
