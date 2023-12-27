package org.folio.sidecar.integration.keycloak.filter;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.utils.RoutingUtils.getParsedSystemToken;
import static org.folio.sidecar.utils.RoutingUtils.getParsedToken;
import static org.folio.sidecar.utils.RoutingUtils.getScRoutingEntry;
import static org.folio.sidecar.utils.RoutingUtils.getTenant;
import static org.folio.sidecar.utils.RoutingUtils.hasNoPermissionsRequired;
import static org.folio.sidecar.utils.RoutingUtils.isSelfRequest;
import static org.folio.sidecar.utils.RoutingUtils.isSystemRequest;
import static org.folio.sidecar.utils.SecurityUtils.JWT_SESSION_STATE_CLAIM;

import com.github.benmanes.caffeine.cache.Cache;
import io.quarkus.security.UnauthorizedException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.keycloak.KeycloakClient;
import org.folio.sidecar.service.filter.IngressRequestFilter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakAuthorizationFilter implements IngressRequestFilter {

  private static final String CACHE_KEY_DELIMITER = "#";
  private static final String KC_PERMISSION_NAME = "kcPermissionName";

  private final KeycloakClient keycloakClient;
  private final Cache<String, JsonWebToken> authTokenCache;

  /**
   * Evaluates if a user has access to a module endpoint by obtaining RPT token from Keycloak.
   *
   * @param routingContext {@link RoutingContext} object to analyze
   * @return succeeded {@link Future} if access is granted.
   */
  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    var permission = resolvePermission(routingContext);
    routingContext.put(KC_PERMISSION_NAME, resolvePermission(routingContext));
    var tenantName = getTenant(routingContext);
    log.info("Authorizing request to: {} for tenant: {}", permission, tenantName);

    return findCachedAccessToken(routingContext, permission, tenantName)
      .map(jwt -> succeededFuture(routingContext))
      .orElseGet(() -> authorizeAndCacheToken(routingContext))
      .map(KeycloakAuthorizationFilter::removePermissionNameValue)
      .onFailure(error -> removePermissionNameValue(routingContext));
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return isSystemRequest(rc) || hasNoPermissionsRequired(rc) || isSelfRequest(rc);
  }

  @Override
  public int getOrder() {
    return 160;
  }

  private Optional<JsonWebToken> findCachedAccessToken(RoutingContext rc, String permission, String tenantName) {
    return Stream.of(getParsedToken(rc), getParsedSystemToken(rc))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(token -> isCachedToken(permission, tenantName, token))
      .findAny();
  }

  private boolean isCachedToken(String permission, String tenantName, JsonWebToken token) {
    var cacheKey = getAccessTokenCacheKey(permission, tenantName, token);
    return authTokenCache.getIfPresent(cacheKey) != null;
  }

  private Future<RoutingContext> authorizeAndCacheToken(RoutingContext rc) {
    return getParsedToken(rc)
      .map(accessToken -> authorizeAndCacheToken(accessToken, rc)
        .recover(error -> authorizeAndCacheSystemToken(rc, error)))
      .orElseGet(() -> authorizeAndCacheSystemToken(rc, null));
  }

  private Future<RoutingContext> authorizeAndCacheToken(JsonWebToken jwt, RoutingContext rc) {
    var tenant = getTenant(rc);
    var permission = getKeycloakPermissionName(rc);

    return keycloakClient.evaluatePermissions(tenant, permission, jwt.getRawToken())
      .flatMap(httpResponse -> processAuthorizationResponse(jwt, rc, httpResponse))
      .otherwise(error -> handleAuthorizationError(error, tenant, permission));
  }

  private Future<RoutingContext> authorizeAndCacheSystemToken(RoutingContext routingContext, Throwable error) {
    return getParsedSystemToken(routingContext)
      .map(systemToken -> authorizeAndCacheToken(systemToken, routingContext))
      .orElseGet(() -> failedFuture(prepareSystemTokenError(error)));
  }

  private static Exception prepareSystemTokenError(Throwable error) {
    var errorMessage = "Failed to find system token in request";
    if (error instanceof UnauthorizedException) {
      return new UnauthorizedException("Unauthorized", new ForbiddenException(errorMessage));
    }

    return new UnauthorizedException(errorMessage, error);
  }

  private static RoutingContext handleAuthorizationError(Throwable throwable, String tenant, String permission) {
    if (throwable instanceof ForbiddenException || throwable instanceof UnauthorizedException) {
      throw (RuntimeException) throwable;
    }

    var errorMsg = String.format("Failed to authorize request: tenant = %s, permission = %s", tenant, permission);
    throw new ForbiddenException(errorMsg, throwable);
  }

  private Future<RoutingContext> processAuthorizationResponse(JsonWebToken accessToken, RoutingContext routingContext,
    HttpResponse<Buffer> httpResponse) {
    var tenant = getTenant(routingContext);
    var permission = getKeycloakPermissionName(routingContext);

    var statusCode = httpResponse.statusCode();
    if (statusCode == FORBIDDEN.code()) {
      return failedFuture(new ForbiddenException("Access Denied"));
    }

    if (statusCode == UNAUTHORIZED.code()) {
      return failedFuture(new UnauthorizedException("Unauthorized"));
    }

    if (statusCode != OK.code()) {
      var msgTemplate = "Failed to authorize request: status = %s, tenant = %s, permission = %s";
      var errorMessage = String.format(msgTemplate, statusCode, tenant, permission);
      return failedFuture(new ForbiddenException(errorMessage));
    }

    var cacheKey = getAccessTokenCacheKey(permission, tenant, accessToken);
    log.debug("Caching access token: key = {}", cacheKey);
    authTokenCache.put(cacheKey, accessToken);
    return succeededFuture(routingContext);
  }

  private static String getKeycloakPermissionName(RoutingContext rc) {
    return rc.get(KC_PERMISSION_NAME);
  }

  private static String resolvePermission(RoutingContext rc) {
    var routingEntry = getScRoutingEntry(rc);
    return routingEntry.getRoutingEntry().getStaticPath() + CACHE_KEY_DELIMITER + resolveRequestScope(rc);
  }

  private static String resolveRequestScope(RoutingContext rc) {
    return rc.request().method().name();
  }

  private static String getAccessTokenCacheKey(String permission, String tenantName, JsonWebToken authToken) {
    var keyJoiner = new StringJoiner(CACHE_KEY_DELIMITER);
    keyJoiner.add(permission);
    keyJoiner.add(tenantName);
    if (authToken.containsClaim(JWT_SESSION_STATE_CLAIM)) { // a service user token does not have session_state claim
      keyJoiner.add(authToken.getClaim(JWT_SESSION_STATE_CLAIM));
    }
    keyJoiner.add(Long.toString(authToken.getExpirationTime()));
    return keyJoiner.toString();
  }

  private static RoutingContext removePermissionNameValue(RoutingContext rc) {
    rc.remove(KC_PERMISSION_NAME);
    return rc;
  }
}
