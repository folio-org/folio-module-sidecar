package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT;
import static org.folio.sidecar.utils.FutureUtils.tryRecoverFrom;

import com.github.benmanes.caffeine.cache.Cache;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ClientErrorException;
import java.util.Map.Entry;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.users.model.User;
import org.folio.sidecar.service.CacheInvalidatable;
import org.folio.sidecar.service.token.TokenCacheFactory;

@Log4j2
@ApplicationScoped
public class KeycloakImpersonationService implements CacheInvalidatable {

  private final KeycloakClient keycloakClient;
  private final CredentialService credentialService;
  private final Cache<String, TokenResponse> tokenCache;

  public KeycloakImpersonationService(KeycloakClient keycloakClient, CredentialService credentialService,
    TokenCacheFactory cacheFactory) {
    this.keycloakClient = keycloakClient;
    this.tokenCache = cacheFactory.createCache();
    this.credentialService = credentialService;
  }

  public Future<TokenResponse> getUserToken(String targetTenant, User user) {
    var key = buildCacheKey(targetTenant, user.getId());
    var userToken = tokenCache.getIfPresent(key);
    if (userToken != null) {
      log.debug("User token found in cache: username = {}, targetTenant = {}", user.getUsername(), targetTenant);
      return succeededFuture(userToken);
    }

    return impersonateUser(targetTenant, user, key)
      .recover(tryRecoverFrom(ClientErrorException.class, resetCredentialsAndImpersonateUser(targetTenant, user, key)));
  }

  private Future<TokenResponse> impersonateUser(String targetTenant, User user, String cacheKey) {
    return credentialService.getImpersonationClientCredentials(targetTenant)
      .compose(credentials -> keycloakClient.impersonateUserToken(targetTenant, credentials, user.getUsername()))
      .compose(tokenResponse -> handelAndCacheResponse(targetTenant, user, cacheKey, tokenResponse));
  }

  private Function<ClientErrorException, Future<TokenResponse>> resetCredentialsAndImpersonateUser(String tenant,
    User user, String cacheKey) {
    return exc -> {
      log.debug("Recovering from impersonation exception by resetting impersonation client credentials and retrying: " +
        "tenant = {}, username = {}", tenant, user.getUsername());

      credentialService.resetImpersonationClientCredentials(tenant);

      return impersonateUser(tenant, user, cacheKey);
    };
  }

  private Future<TokenResponse> handelAndCacheResponse(String targetTenant, User user, String cacheKey,
                                                       HttpResponse<Buffer> tokenResponse) {
    var statusCode = tokenResponse.statusCode();

    if (statusCode != HttpResponseStatus.OK.code()) {
      log.error("Failed to impersonate user token: response = {}", tokenResponse.bodyAsString());
      var exception = new ClientErrorException("Failed to impersonate user token", statusCode);
      return failedFuture(exception);
    }

    var token = tokenResponse.bodyAsJson(TokenResponse.class);
    tokenCache.put(cacheKey, token);

    log.debug("User token impersonated and cached: username = {}, targetTenant = {}", user.getUsername(),
      targetTenant);
    return succeededFuture(token);
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
