package org.folio.sidecar.service.token;

import static java.util.Collections.emptySet;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.utils.CollectionUtils.isNotEmpty;
import static org.folio.sidecar.utils.FutureUtils.executeAndGet;
import static org.folio.sidecar.utils.TokenUtils.tokenResponseAsString;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
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
  private final AsyncLoadingCache<String, TokenResponse> tokenCache;
  private final Vertx vertx;

  @Inject
  ServiceTokenProvider(KeycloakService keycloakService, KeycloakProperties properties,
                       AsyncSecureStore secureStore, AsyncTokenCacheFactory cacheFactory, Vertx vertx) {
    this.keycloakService = keycloakService;
    this.properties = properties;
    this.secureStore = secureStore;
    this.tokenCache = cacheFactory.createCache(this::retrieveToken);
    this.vertx = vertx;
  }

  @SuppressWarnings("unused")
  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void syncCache(EntitlementsEvent entitlementsEvent) {
    var tenants = entitlementsEvent.getTenants();
    syncTenantCache(tenants);
  }

  /**
   * Obtains an admin access token for master realm using client_credentials flow.
   *
   * @return {@link Future} containing the access token.
   */
  public Future<String> getAdminToken() {
    return getTokenInternal(SUPER_TENANT);
  }

  /**
   * Obtains a service access token using client_credentials flow.
   *
   * @param rc {@link RoutingContext} object to analyze
   * @return {@link Future} containing the access token.
   */
  public Future<String> getToken(RoutingContext rc) {
    var tenantName = RoutingUtils.getTenant(rc);
    return getTokenInternal(tenantName, rc);
  }

  public String getTokenSync(RoutingContext rc) {
    return getToken(rc).result();
  }

  private Future<String> getTokenInternal(String tenant, RoutingContext rc) {
    var rq = rc.request();
    var requestId = rq.getHeader(REQUEST_ID);
    log.info("Getting service token [method: {}, path: {}, requestId: {}, tenant: {}]",
      rq.method(), rq.path(), requestId, tenant);

    return Future.fromCompletionStage(tokenCache.get(tenant)).map(TokenResponse::getAccessToken);
  }

  private Future<String> getTokenInternal(String tenant) {
    log.info("Getting service token for tenant: {}", tenant);
    return Future.fromCompletionStage(tokenCache.get(tenant)).map(TokenResponse::getAccessToken);
  }

  private TokenResponse retrieveToken(String tenant) {
    var cred = SUPER_TENANT.equalsIgnoreCase(tenant)
      ? getAdminClientCredentials()
      : getServiceClientCredentials(tenant);

    var tokenFuture = cred.compose(credentials -> keycloakService.obtainToken(tenant, credentials))
      .map(tokenResponse -> {
        log.debug("Service token obtained: token = {}, tenant = {}",
          () -> tokenResponseAsString(tokenResponse), () -> tenant);
        return tokenResponse;
      });

    return executeAndGet(tokenFuture, throwable -> {
      log.warn("Failed to obtain service token: message = {}", throwable.getMessage(), throwable);
      return null;
    });
  }

  private Future<ClientCredentials> getAdminClientCredentials() {
    log.info("Retrieving admin client credentials from secret store");

    var clientId = properties.getAdminClientId();
    return secureStore.get(SecureStoreUtils.globalStoreKey(clientId))
      .map(secret -> ClientCredentials.of(clientId, secret));
  }

  private Future<ClientCredentials> getServiceClientCredentials(String tenantName) {
    log.info("Retrieving service client credentials from secret store: tenant = {}", tenantName);

    var clientId = properties.getServiceClientId();
    return secureStore.get(SecureStoreUtils.tenantStoreKey(tenantName, clientId))
      .map(secret -> ClientCredentials.of(clientId, secret));
  }

  private void syncTenantCache(Set<String> tenants) {
    log.info("Synchronizing service token cache...");
    var enabledTenants = tenants == null ? emptySet() : tenants;
    var cachedTenants = tokenCache.asMap().keySet();

    if (isNotEmpty(cachedTenants)) {
      var toInvalidate = cachedTenants.stream().filter(cached -> !enabledTenants.contains(cached)).toList();
      log.info("Invalidating service token cache for tenants: tenants = {}", toInvalidate);
      tokenCache.synchronous().invalidateAll(toInvalidate);
    }

    if (isNotEmpty(tenants)) {
      var toLoad = tenants.stream().filter(t -> !cachedTenants.contains(t)).toList();
      log.info("Retrieving service token cache for tenants: tenants = {}", toLoad);
      tokenCache.getAll(toLoad);
    }
  }
}
