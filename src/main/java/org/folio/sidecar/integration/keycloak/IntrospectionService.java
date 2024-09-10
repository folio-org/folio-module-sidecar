package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.join;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT_ALL;
import static org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse.INACTIVE_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.utils.JwtUtils.getSessionIdClaim;
import static org.folio.sidecar.utils.JwtUtils.getUserIdClaim;
import static org.folio.sidecar.utils.RoutingUtils.getOriginTenant;
import static org.folio.sidecar.utils.RoutingUtils.getParsedToken;
import static org.folio.sidecar.utils.SecureStoreUtils.tenantStoreKey;

import com.github.benmanes.caffeine.cache.Cache;
import io.quarkus.security.UnauthorizedException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse;
import org.folio.sidecar.model.ClientCredentials;
import org.folio.sidecar.service.CacheInvalidatable;
import org.folio.sidecar.service.store.AsyncSecureStore;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class IntrospectionService implements CacheInvalidatable {

  private final KeycloakProperties properties;
  private final KeycloakClient keycloakClient;
  private final AsyncSecureStore secureStore;
  private final Cache<String, TokenIntrospectionResponse> tokenCache;

  public Future<RoutingContext> checkActiveToken(RoutingContext ctx) {
    return introspectToken(ctx)
      .map(TokenIntrospectionResponse::isActive)
      .flatMap(IntrospectionService::handleTokenStatus);
  }

  @Override
  public void invalidate(LogoutEvent event) {
    if (LOGOUT_ALL == event.getType()) {
      tokenCache.asMap().forEach((key, value) -> {
        if (key.contains(event.getUserId())) {
          tokenCache.put(key, INACTIVE_TOKEN);
        }
      });
      return;
    }

    tokenCache.asMap().forEach((key, value) -> {
      if (key.contains(event.getUserId()) && key.contains(event.getSessionId())) {
        tokenCache.put(key, INACTIVE_TOKEN);
      }
    });
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
    var key = cacheKey(tenant, userId.get(), sessionId);

    var cachedIntrospection = tokenCache.getIfPresent(key);
    if (cachedIntrospection != null) {
      return succeededFuture(cachedIntrospection);
    }

    var token = ctx.request().getHeader(TOKEN);
    var clientId = tenant + properties.getLoginClientSuffix();
    return secureStore.get(tenantStoreKey(tenant, clientId))
      .map(clientSecret -> ClientCredentials.of(clientId, clientSecret))
      .flatMap(client -> keycloakClient.introspectToken(tenant, client, token))
      .flatMap(response -> handelAndCacheResponse(response, key));
  }

  private Future<TokenIntrospectionResponse> handelAndCacheResponse(HttpResponse<Buffer> response, String key) {
    var statusCode = response.statusCode();
    if (statusCode != 200) {
      log.warn("Failed to introspect user token: response = {}", response::bodyAsString);
      return failedFuture(new UnauthorizedException("Failed to introspect user token"));
    }
    var introspectionResponse = response.bodyAsJson(TokenIntrospectionResponse.class);
    tokenCache.put(key, introspectionResponse.isActive() ? introspectionResponse : INACTIVE_TOKEN);
    return succeededFuture(introspectionResponse);
  }

  private static Future<RoutingContext> handleTokenStatus(Boolean isActive) {
    if (isActive) {
      return succeededFuture();
    }

    return failedFuture(new UnauthorizedException("User token is not active"));
  }

  private static String cacheKey(String tenant, String userId, String tokenSessionId) {
    return join("#", tenant, userId, tokenSessionId);
  }
}
