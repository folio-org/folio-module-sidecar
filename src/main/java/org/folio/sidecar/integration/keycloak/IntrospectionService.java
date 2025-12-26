package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.join;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT_ALL;
import static org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse.INACTIVE_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.utils.FutureUtils.tryRecoverFrom;
import static org.folio.sidecar.utils.JwtUtils.getSessionIdClaim;
import static org.folio.sidecar.utils.JwtUtils.getTokenExpirationTime;
import static org.folio.sidecar.utils.JwtUtils.getUserIdClaim;
import static org.folio.sidecar.utils.RoutingUtils.getOriginTenant;
import static org.folio.sidecar.utils.RoutingUtils.getParsedToken;
import static org.folio.sidecar.utils.TokenUtils.tokenHash;

import com.github.benmanes.caffeine.cache.Cache;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.UnauthorizedException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse;
import org.folio.sidecar.service.CacheInvalidatable;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class IntrospectionService implements CacheInvalidatable {

  private final KeycloakClient keycloakClient;
  private final CredentialService credentialService;
  private final Cache<String, TokenIntrospectionResponse> tokenCache;

  public Future<RoutingContext> checkActiveToken(RoutingContext ctx) {
    return introspectToken(ctx)
      .map(TokenIntrospectionResponse::isActive)
      .flatMap(IntrospectionService::handleTokenStatus);
  }

  @Override
  public void invalidate(LogoutEvent event) {
    tokenCache.asMap().keySet().stream()
      .filter(key -> shouldInvalidate(event, key))
      .forEach(key -> tokenCache.put(key, INACTIVE_TOKEN));
  }

  private Future<TokenIntrospectionResponse> introspectToken(RoutingContext ctx) {
    var parsedToken = getParsedToken(ctx);
    if (parsedToken.isEmpty()) {
      return failedFuture(new UnauthorizedException("Parsed token not found in request"));
    }
    var userId = getUserIdClaim(parsedToken.get());
    if (userId.isEmpty()) {
      return failedFuture(new UnauthorizedException("user_id claim not found in token"));
    }

    var tenant = getOriginTenant(ctx);
    var sessionId = getSessionIdClaim(parsedToken.get());
    var expirationTime = getTokenExpirationTime(parsedToken.get());
    var key = cacheKey(tenant, userId.get(), sessionId, expirationTime);

    var cachedIntrospection = tokenCache.getIfPresent(key);
    if (cachedIntrospection != null) {
      return succeededFuture(cachedIntrospection);
    }

    var token = ctx.request().getHeader(TOKEN);
    return introspectToken(tenant, token, key)
      .recover(tryRecoverFrom(UnauthorizedException.class, resetCredentialsAndIntrospectToken(tenant, token, key)));
  }

  private Future<TokenIntrospectionResponse> introspectToken(String tenant, String token, String cacheKey) {
    return credentialService.getLoginClientCredentials(tenant)
      .compose(client -> keycloakClient.introspectToken(tenant, client, token))
      .compose(response -> handelAndCacheResponse(response, cacheKey));
  }

  private Function<UnauthorizedException, Future<TokenIntrospectionResponse>> resetCredentialsAndIntrospectToken(
    String tenant, String token, String cacheKey) {
    return exc -> {
      log.debug("Recovering from Unauthorized exception by resetting login credentials and retrying: "
        + "tenant = {}, token = {}", () -> tenant, () -> tokenHash(token));

      credentialService.resetLoginClientCredentials(tenant);

      return introspectToken(tenant, token, cacheKey);
    };
  }

  private Future<TokenIntrospectionResponse> handelAndCacheResponse(HttpResponse<Buffer> response, String key) {
    var statusCode = response.statusCode();
    if (statusCode != HttpResponseStatus.OK.code()) {
      log.warn("Failed to introspect user token: response = {}, status = {}", response::bodyAsString, () -> statusCode);
      return failedFuture(new UnauthorizedException("Failed to introspect user token"));
    }
    var introspectionResponse = response.bodyAsJson(TokenIntrospectionResponse.class);
    if (introspectionResponse.isActive()) {
      tokenCache.put(key, introspectionResponse);
    }
    return succeededFuture(introspectionResponse);
  }

  private static Future<RoutingContext> handleTokenStatus(Boolean isActive) {
    if (isActive) {
      return succeededFuture();
    }

    return failedFuture(new UnauthorizedException("User token is not active"));
  }

  private static boolean shouldInvalidate(LogoutEvent event, String key) {
    return LOGOUT_ALL == event.getType()
      ? key.contains(event.getUserId())
      : key.contains(event.getUserId()) && key.contains(event.getSessionId());
  }

  private static String cacheKey(String tenant, String userId, String tokenSessionId, Long expirationTime) {
    return join("#", tenant, userId, tokenSessionId, String.valueOf(expirationTime));
  }
}
