package org.folio.sidecar.service.token;

import static java.util.Collections.emptySet;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.service.token.TokenUtils.tokenResponseAsString;
import static org.folio.sidecar.utils.CollectionUtils.isNotEmpty;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.UserCredentials;
import org.folio.sidecar.service.store.AsyncSecureStore;
import org.folio.sidecar.utils.RoutingUtils;
import org.folio.sidecar.utils.SecureStoreUtils;

@Log4j2
@ApplicationScoped
public class SystemUserTokenProvider {

  private final KeycloakService keycloakService;
  private final AsyncSecureStore secureStore;
  private final KeycloakProperties keycloakProperties;
  private final ModuleProperties moduleProperties;
  private final AsyncLoadingCache<String, TokenResponse> tokenCache;

  @Inject
  SystemUserTokenProvider(KeycloakService keycloakService, KeycloakProperties properties,
    ModuleProperties moduleProperties, AsyncTokenCacheFactory cacheFactory, AsyncSecureStore secureStore) {
    this.keycloakService = keycloakService;
    this.keycloakProperties = properties;
    this.secureStore = secureStore;
    this.moduleProperties = moduleProperties;
    this.tokenCache = cacheFactory.createCache(this::retrieveToken);
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
   * @param rc - {@link RoutingContext} object to handle
   * @return {@link Future} containing the access token.
   */
  public Future<String> getToken(RoutingContext rc) {
    var tenant = RoutingUtils.getTenant(rc);
    var rq = rc.request();
    var requestId = rq.getHeader(REQUEST_ID);
    log.info("Getting system user token [method: {}, path: {}, requestId: {}, tenant: {}]",
      rq.method(), rq.path(), requestId, tenant);

    return Future.fromCompletionStage(tokenCache.get(tenant)).map(TokenResponse::getAccessToken);
  }

  public String getTokenSync(RoutingContext rc) {
    return getToken(rc).result();
  }

  private TokenResponse retrieveToken(String tenant) {
    var username = moduleProperties.getName();
    return getUserCredentials(tenant, username)
      .compose(user -> obtainAndCacheToken(tenant, user, username))
      .result();
  }

  private Future<TokenResponse> obtainAndCacheToken(String tenant, UserCredentials user, String username) {
    return getClientCredentials(tenant)
      .compose(client -> authUser(tenant, user, username, client))
      .map(cacheToken(tenant))
      .onFailure(e -> log.warn("Cannot obtain token: message = {}", e.getMessage(), e));
  }

  private Future<TokenResponse> authUser(String tenant, UserCredentials user, String username,
    ClientCredentials client) {
    log.info("Authenticating system user: user = {}, tenant = {}", username, tenant);
    return keycloakService.obtainUserToken(tenant, client, user);
  }

  private UnaryOperator<TokenResponse> cacheToken(String tenant) {
    return tokenResponse -> {
      log.debug("System user token obtained: token = {}, tenant = {}",
        () -> tokenResponseAsString(tokenResponse), () -> tenant);
      return tokenResponse;
    };
  }

  private Future<ClientCredentials> getClientCredentials(String tenant) {
    var clientId = tenant + keycloakProperties.getLoginClientSuffix();
    return secureStore.get(SecureStoreUtils.tenantStoreKey(tenant, clientId))
      .map(secret -> {
        log.info("Client credentials obtained: clientId = {}, tenant = {}", clientId, tenant);
        return ClientCredentials.of(clientId, secret);
      });
  }

  private Future<UserCredentials> getUserCredentials(String tenant, String username) {
    return secureStore.get(SecureStoreUtils.tenantStoreKey(tenant, username))
      .map(password -> {
        log.info("User credentials obtained: username = {}, tenant = {}", username, tenant);
        return UserCredentials.of(username, password);
      });
  }

  private void syncTenantCache(Set<String> tenants) {
    log.info("Synchronizing system users cache...");
    var enabledTenants = tenants == null ? emptySet() : tenants;
    var cachedTenants = tokenCache.asMap().keySet();

    if (isNotEmpty(cachedTenants)) {
      var toInvalidate = cachedTenants.stream().filter(cached -> !enabledTenants.contains(cached)).toList();
      log.info("Invalidating system users tokens fo tenants: tenants = {}", toInvalidate);
      tokenCache.synchronous().invalidateAll(toInvalidate);
    }

    if (isNotEmpty(tenants)) {
      var toLoad = tenants.stream().filter(t -> !cachedTenants.contains(t)).toList();
      log.info("Retrieving system users tokens fo tenants: tenants = {}", toLoad);
      tokenCache.getAll(toLoad);
    }
  }
}
