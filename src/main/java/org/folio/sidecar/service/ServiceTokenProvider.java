package org.folio.sidecar.service;

import com.github.benmanes.caffeine.cache.Cache;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.service.store.AsyncSecureStore;
import org.folio.sidecar.utils.RoutingUtils;
import org.folio.sidecar.utils.SecureStoreUtils;

@Log4j2
@ApplicationScoped
public class ServiceTokenProvider {

  private static final String SUPER_TENANT = "master";
  private final KeycloakService keycloakService;
  private final AsyncSecureStore secureStore;
  private final KeycloakProperties properties;
  private final Cache<String, TokenResponse> tokenCache;

  @Inject
  ServiceTokenProvider(KeycloakService keycloakService, KeycloakProperties properties,
    AsyncSecureStore secureStore, TokenCacheFactory cacheFactory) {
    this.keycloakService = keycloakService;
    this.properties = properties;
    this.secureStore = secureStore;
    this.tokenCache = cacheFactory.createCache();
  }

  @SuppressWarnings("unused")
  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void syncCache(EntitlementsEvent entitlementsEvent) {
    var tenants = entitlementsEvent.getTenants();
    invalidateRemovedTenantCache(tenants);
  }

  /**
   * Obtains an admin access token for master realm using client_credentials flow.
   *
   * @return {@link Future} containing the access token.
   */
  public Future<String> getAdminToken() {
    return getToken(SUPER_TENANT, this::obtainAdminToken);
  }

  /**
   * Obtains a service access token using client_credentials flow.
   *
   * @param rc {@link RoutingContext} object to analyze
   * @return {@link Future} containing the access token.
   */
  public Future<String> getServiceToken(RoutingContext rc) {
    var tenantName = RoutingUtils.getTenant(rc);
    return getToken(tenantName, () -> obtainServiceToken(tenantName, rc));
  }

  private Future<String> getToken(String tenantName, Supplier<Future<TokenResponse>> tokenLoader) {
    var cachedValue = tokenCache.getIfPresent(tenantName);
    if (cachedValue != null) {
      return Future.succeededFuture(cachedValue.getAccessToken());
    }
    return obtainAndCacheToken(tenantName, tokenLoader).map(TokenResponse::getAccessToken);
  }

  private Future<TokenResponse> obtainAndCacheToken(String tenantName, Supplier<Future<TokenResponse>> tokenProvider) {
    log.info("Authenticating service client for tenant: {}", tenantName);
    return tokenProvider.get().onSuccess(token -> tokenCache.put(tenantName, token))
      .onFailure(e -> log.warn("Failed to obtain service token", e));
  }

  private Future<TokenResponse> obtainAdminToken() {
    return getAdminClientCredentials()
      .compose(credentials -> keycloakService.obtainToken(SUPER_TENANT, credentials));
  }

  private Future<TokenResponse> obtainServiceToken(String tenantName, RoutingContext rc) {
    return getServiceClientCredentials(tenantName)
      .compose(credentials -> keycloakService.obtainToken(tenantName, credentials, rc));
  }

  private Future<ClientCredentials> getAdminClientCredentials() {
    log.info("Retrieving admin client credentials from secret store");

    var clientId = properties.getAdminClientId();
    return secureStore.get(SecureStoreUtils.globalStoreKey(clientId))
      .map(secret -> ClientCredentials.of(clientId, secret));
  }

  private Future<ClientCredentials> getServiceClientCredentials(String tenantName) {
    log.info("Retrieving service client credentials from secret store");

    var clientId = properties.getServiceClientId();
    return secureStore.get(SecureStoreUtils.tenantStoreKey(tenantName, clientId))
      .map(secret -> ClientCredentials.of(clientId, secret));
  }

  private void invalidateRemovedTenantCache(Set<String> tenants) {
    log.info("Invalidating obsolete cache records");
    var cachedTenants = tokenCache.asMap().keySet();
    cachedTenants.stream()
      .filter(cached -> !tenants.contains(cached))
      .forEach(tokenCache::invalidate);
  }
}
