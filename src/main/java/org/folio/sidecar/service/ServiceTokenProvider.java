package org.folio.sidecar.service;

import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;

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
import org.folio.sidecar.utils.CollectionUtils;
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
    syncTenantCache(tenants);
  }

  public void syncTenantCache(Set<String> tenants) {
    if (CollectionUtils.isNotEmpty(tenants)) {
      log.info("Synchronizing service token cache");
      var cachedTenants = tokenCache.asMap().keySet();
      cachedTenants.stream().filter(cached -> !tenants.contains(cached)).forEach(tokenCache::invalidate);
      tenants.stream().filter(t -> !cachedTenants.contains(t))
        .forEach(t -> obtainAndCacheToken(t, () -> obtainServiceToken(t)));
    }
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
    return getToken(tenantName, () -> obtainServiceToken(tenantName, rc), rc);
  }

  public String getServiceTokenFromCache(String tenantName) {
    var cachedValue = tokenCache.getIfPresent(tenantName);
    if (cachedValue != null) {
      return cachedValue.getAccessToken();
    }
    throw new IllegalStateException("Token not found in cache");
  }

  private Future<String> getToken(String tenantName, Supplier<Future<TokenResponse>> tokenLoader, RoutingContext rc) {
    var rq = rc.request();
    log.info("Getting service token [method: {}, path: {}, requestId: {}]",
      rq.method(), rq.path(), rq.getHeader(REQUEST_ID));

    var cachedValue = tokenCache.getIfPresent(tenantName);
    if (cachedValue != null) {
      log.info("Token found in cache [requestId: {}]",
        rq.getHeader(REQUEST_ID));

      return Future.succeededFuture(cachedValue.getAccessToken());
    }

    log.info("Token not found in cache, obtaining new token [requestId: {}]",
      rq.getHeader(REQUEST_ID));
    return obtainAndCacheToken(tenantName, tokenLoader, rc).map(TokenResponse::getAccessToken);
  }

  private Future<String> getToken(String tenantName, Supplier<Future<TokenResponse>> tokenLoader) {
    var cachedValue = tokenCache.getIfPresent(tenantName);
    if (cachedValue != null) {
      return Future.succeededFuture(cachedValue.getAccessToken());
    }
    return obtainAndCacheToken(tenantName, tokenLoader).map(TokenResponse::getAccessToken);
  }

  private Future<TokenResponse> obtainAndCacheToken(String tenantName, Supplier<Future<TokenResponse>> tokenProvider,
    RoutingContext rc) {
    log.info("Authenticating service client for tenant: {}. [requestId: {}]", tenantName,
      rc.request().getHeader(REQUEST_ID));

    return tokenProvider.get().onSuccess(token -> {
      tokenCache.put(tenantName, token);

      log.info("Token obtained and cached for tenant: {}. [requestId: {}]",
        tenantName, rc.request().getHeader(REQUEST_ID));
    })
    .onFailure(e -> log.warn("Failed to obtain service token", e));
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
    return getServiceClientCredentials(tenantName, rc)
      .compose(credentials -> keycloakService.obtainToken(tenantName, credentials, rc));
  }

  private Future<TokenResponse> obtainServiceToken(String tenantName) {
    return getServiceClientCredentials(tenantName)
      .compose(credentials -> keycloakService.loadToken(tenantName, credentials));
  }

  private Future<ClientCredentials> getAdminClientCredentials() {
    log.info("Retrieving admin client credentials from secret store");

    var clientId = properties.getAdminClientId();
    return secureStore.get(SecureStoreUtils.globalStoreKey(clientId))
      .map(secret -> ClientCredentials.of(clientId, secret));
  }

  private Future<ClientCredentials> getServiceClientCredentials(String tenantName, RoutingContext rc) {
    log.info("Retrieving service client credentials from secret store [requestId: {}]",
      rc.request().getHeader(REQUEST_ID));

    var clientId = properties.getServiceClientId();
    return secureStore.get(SecureStoreUtils.tenantStoreKey(tenantName, clientId))
      .map(secret -> ClientCredentials.of(clientId, secret));
  }

  private Future<ClientCredentials> getServiceClientCredentials(String tenantName) {
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
