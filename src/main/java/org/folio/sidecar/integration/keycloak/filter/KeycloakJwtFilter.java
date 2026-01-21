package org.folio.sidecar.integration.keycloak.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Optional.ofNullable;
import static org.folio.jwt.openid.JsonWebTokenParser.INVALID_SEGMENTS_JWT_ERROR_MSG;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.AUTHORIZATION;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.service.filter.IngressFilterOrder.KEYCLOAK_JWT;
import static org.folio.sidecar.utils.JwtUtils.getUserIdClaim;
import static org.folio.sidecar.utils.JwtUtils.trimTokenBearer;
import static org.folio.sidecar.utils.RoutingUtils.getParsedSystemToken;
import static org.folio.sidecar.utils.RoutingUtils.hasNoPermissionsRequired;
import static org.folio.sidecar.utils.RoutingUtils.hasUserIdHeader;
import static org.folio.sidecar.utils.RoutingUtils.isSelfRequest;
import static org.folio.sidecar.utils.RoutingUtils.isSystemRequest;
import static org.folio.sidecar.utils.RoutingUtils.isTimerRequest;
import static org.folio.sidecar.utils.RoutingUtils.putOriginTenant;
import static org.folio.sidecar.utils.RoutingUtils.putParsedToken;
import static org.folio.sidecar.utils.RoutingUtils.setUserIdHeader;

import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.keycloak.AsyncJsonWebTokenParser;
import org.folio.sidecar.service.filter.IngressRequestFilter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakJwtFilter implements IngressRequestFilter {

  private static final String FAILED_TO_PARSE_JWT_ERROR_MSG = "Failed to parse JWT";

  private final AsyncJsonWebTokenParser asyncJsonWebTokenParser;

  /**
   * When a module (for example mod-circulation) registers events in mod-pubsub the request is sent without
   * x-okapi-token (as we don't pass it during tenant install), mod-pubsub-client lib adds a default "dummy" token to
   * such requests and therefore sidecar should be able to process such request.
   *
   * @param rc - {@link RoutingContext} routing context
   * @return {@link Future} of {@link RoutingContext} object
   */
  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    var future = getParsedSystemToken(rc).isPresent()
      ? authenticateRequestWithSystemJwt(rc)
      : authenticateRequest(rc);
    return future.recover(error -> handleFailedTokenParsing(rc, error));
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return isSystemRequest(rc) && !isTimerRequest(rc);
  }

  @Override
  public int getOrder() {
    return KEYCLOAK_JWT.getOrder();
  }

  private Future<RoutingContext> authenticateRequest(RoutingContext routingContext) {
    return findAccessToken(routingContext)
      .map(accessToken -> tryParseAccessToken(accessToken, routingContext))
      .orElseGet(() -> failedFuture(new UnauthorizedException("Failed to find JWT in request")));
  }

  private Future<RoutingContext> authenticateRequestWithSystemJwt(RoutingContext routingContext) {
    return findAccessToken(routingContext)
      .map(accessToken -> findAccessTokenWhenSystemJwtPresent(accessToken, routingContext))
      .orElseGet(() -> succeededFuture(routingContext));
  }

  private Optional<String> findAccessToken(RoutingContext rc) {
    var headers = rc.request().headers();
    var okapiToken = getToken(rc);
    var authToken = ofNullable(headers.get(AUTHORIZATION)).orElse(okapiToken);

    if (authToken == null) {
      return Optional.empty();
    }

    var accessToken = trimTokenBearer(authToken);
    if (okapiToken != null && !accessToken.equals(okapiToken)) {
      throw new BadRequestException("X-Okapi-Token is not equal to Authorization token");
    }

    headers.set(TOKEN, accessToken);
    headers.remove(AUTHORIZATION);

    return Optional.of(accessToken);
  }

  private Future<RoutingContext> tryParseAccessToken(String accessToken, RoutingContext rc) {
    return asyncJsonWebTokenParser.parseAsync(accessToken)
      .map(jsonWebToken -> populateContextAndHeaders(rc, jsonWebToken));
  }

  private Future<RoutingContext> findAccessTokenWhenSystemJwtPresent(String accessToken, RoutingContext rc) {
    return asyncJsonWebTokenParser.parseAsync(accessToken)
      .map(jsonWebToken -> populateContextAndHeaders(rc, jsonWebToken))
      .recover(error -> handleParseExceptionForPresentSystemJwt(rc, error));
  }

  /**
   * Handles failed token parsing. If no permissions are required and the error is not related to parsing JWT or
   * the request is a self request and there is no token provided at all, then returns succeeded future.
   * Otherwise, returns failed future with the error. Token should be parsed and data should be populated
   * even if no permissions are required.
   *
   * @param rc - {@link RoutingContext} routing context
   * @param error - {@link Throwable} error
   * @return {@link Future} of {@link RoutingContext} object
   */
  private static Future<RoutingContext> handleFailedTokenParsing(RoutingContext rc, Throwable error) {
    if (hasNoPermissionsRequired(rc) && !Objects.equals(FAILED_TO_PARSE_JWT_ERROR_MSG, error.getMessage())
      || isSelfRequest(rc) && !hasToken(rc)
      // If system token is present, then we should not fail the request
      || getParsedSystemToken(rc).isPresent()) {
      return succeededFuture(rc);
    }
    return failedFuture(error);
  }

  private static String getToken(RoutingContext rc) {
    return rc.request().headers().get(TOKEN);
  }

  private static boolean hasToken(RoutingContext rc) {
    return getToken(rc) != null;
  }

  private static Future<RoutingContext> handleParseExceptionForPresentSystemJwt(RoutingContext rc, Throwable error) {
    // Special case: dummy tokens from mod-pubsub
    if (error.getCause() instanceof ParseException err
        && Objects.equals(INVALID_SEGMENTS_JWT_ERROR_MSG, err.getMessage())) {
      return succeededFuture(rc);
    }

    // All other errors (JWT parsing and system errors) - pass through unchanged
    return failedFuture(error);
  }

  private static RoutingContext populateContextAndHeaders(RoutingContext rc, JsonWebToken parsedToken) {
    putParsedToken(rc, parsedToken);
    putOriginTenant(rc, parsedToken);
    if (!hasUserIdHeader(rc)) {
      getUserIdClaim(parsedToken).ifPresent(userId -> setUserIdHeader(rc, userId));
    }

    return rc;
  }
}
