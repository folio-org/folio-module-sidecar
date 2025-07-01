package org.folio.sidecar.service.token;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptySet;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.utils.CollectionUtils.isNotEmpty;
import static org.folio.sidecar.utils.FutureUtils.executeAndGet;
import static org.folio.sidecar.utils.FutureUtils.tryRecoverFrom;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;
import static org.folio.sidecar.utils.TokenUtils.tokenResponseAsString;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.integration.keycloak.KeycloakService;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.service.routing.ModuleBootstrapListener;
import org.folio.sidecar.utils.RoutingUtils;

@Log4j2
@ApplicationScoped
@RegisterForReflection
public class SystemUserTokenProvider implements ModuleBootstrapListener {

  private final KeycloakService keycloakService;
  private final CredentialService credentialService;
  private final ModuleProperties moduleProperties;
  private final AsyncLoadingCache<String, TokenResponse> tokenCache;
  private boolean systemUserRequired = false;

  @Inject
  SystemUserTokenProvider(KeycloakService keycloakService, CredentialService credentialService,
    ModuleProperties moduleProperties, AsyncTokenCacheFactory cacheFactory) {
    this.keycloakService = keycloakService;
    this.credentialService = credentialService;
    this.moduleProperties = moduleProperties;
    this.tokenCache = cacheFactory.createCache(this::retrieveToken);
  }

  @SuppressWarnings("unused")
  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void syncCache(EntitlementsEvent event) {
    if (systemUserRequired) {
      var tenants = event.getTenants();
      syncTenantCache(tenants);
    }
  }

  @Override
  public void onModuleBootstrap(ModuleBootstrapDiscovery moduleBootstrap, ChangeType changeType) {
    var previousSystemUserRequired = systemUserRequired;

    systemUserRequired = moduleBootstrap.isSystemUserRequired();
    log.info("\"System user required\" set to: {}", systemUserRequired);

    if (previousSystemUserRequired && !systemUserRequired) { // changed from true to false
      // this shouldn't happen in normal operation, but to be safe, invalidate the cache
      log.info("System user is no longer required, invalidating the token cache");
      tokenCache.synchronous().invalidateAll();
    }
  }

  /**
   * Get a system user access token. If the system user is not required, it returns an empty Optional.
   *
   * @param rc {@link RoutingContext} object to analyze
   * @return {@link Future} containing the access token wrapped in an Optional or
   *   an empty Optional if the system user is not required.
   */
  public Future<Optional<String>> getToken(RoutingContext rc) {
    if (!systemUserRequired) {
      log.debug("System user token is not required, returning empty token");
      return succeededFuture(Optional.empty());
    }

    var tenant = RoutingUtils.getTenant(rc);
    var rq = rc.request();
    var requestId = rq.getHeader(REQUEST_ID);
    log.info("Getting system user token [method: {}, path: {}, requestId: {}, tenant: {}]",
      rq::method, dumpUri(rc), () -> requestId, () -> tenant);

    return Future.fromCompletionStage(tokenCache.get(tenant)).map(TokenResponse::getAccessToken).map(Optional::of);
  }

  TokenResponse retrieveToken(String tenant) throws Exception {
    var username = moduleProperties.getName();

    var tokenFuture = obtainToken(tenant, username)
      .recover(tryRecoverFrom(UnauthorizedException.class, resetCredentialsAndObtainToken(tenant, username)));

    return executeAndGet(tokenFuture);
  }

  private Future<TokenResponse> obtainToken(String tenant, String username) {
    var compositeCreds = Future.all(
      credentialService.getUserCredentials(tenant, username),
      credentialService.getLoginClientCredentials(tenant));

    return compositeCreds
      .compose(creds -> authUser(tenant, creds.resultAt(0), creds.resultAt(1)))
      .map(tokenResponse -> {
        log.debug("System user token obtained: token = {}, tenant = {}",
          () -> tokenResponseAsString(tokenResponse), () -> tenant);
        return tokenResponse;
      });
  }

  private Function<UnauthorizedException, Future<TokenResponse>> resetCredentialsAndObtainToken(String tenant,
    String username) {
    return exc -> {
      log.debug("Recovering from Unauthorized exception by resetting user / login credentials and retrying: "
        + "tenant = {}, username = {}", tenant, username);

      credentialService.resetUserCredentials(tenant, username);
      credentialService.resetLoginClientCredentials(tenant);

      return obtainToken(tenant, username);
    };
  }

  private Future<TokenResponse> authUser(String tenant, UserCredentials user, ClientCredentials client) {
    log.info("Authenticating system user: user = {}, tenant = {}", user.getUsername(), tenant);
    return keycloakService.obtainUserToken(tenant, client, user);
  }

  private void syncTenantCache(Set<String> tenants) {
    log.info("Synchronizing system users cache...");
    var enabledTenants = tenants == null ? emptySet() : tenants;
    var cachedTenants = tokenCache.asMap().keySet();

    if (isNotEmpty(cachedTenants)) {
      var toInvalidate = cachedTenants.stream().filter(cached -> !enabledTenants.contains(cached)).toList();
      log.info("Invalidating system users tokens for tenants: tenants = {}", toInvalidate);
      tokenCache.synchronous().invalidateAll(toInvalidate);
    }

    if (isNotEmpty(tenants)) {
      var toLoad = tenants.stream().filter(t -> !cachedTenants.contains(t)).toList();
      log.info("Retrieving system users tokens for tenants: tenants = {}", toLoad);
      tokenCache.getAll(toLoad);
    }
  }
}
