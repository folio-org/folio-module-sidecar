package org.folio.sidecar.integration.keycloak.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Optional.ofNullable;
import static org.folio.sidecar.integration.keycloak.JsonWebTokenParser.INVALID_SEGMENTS_JWT_ERROR_MSG;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.AUTHORIZATION;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.utils.JwtUtils.getUserIdClaim;
import static org.folio.sidecar.utils.RoutingUtils.getParsedSystemToken;
import static org.folio.sidecar.utils.RoutingUtils.hasNoPermissionsRequired;
import static org.folio.sidecar.utils.RoutingUtils.hasUserIdHeader;
import static org.folio.sidecar.utils.RoutingUtils.isSystemRequest;
import static org.folio.sidecar.utils.RoutingUtils.putParsedToken;
import static org.folio.sidecar.utils.RoutingUtils.setUserIdHeader;
import static org.folio.sidecar.utils.SecurityUtils.trimTokenBearer;

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
import org.folio.sidecar.integration.keycloak.JsonWebTokenParser;
import org.folio.sidecar.service.filter.IngressRequestFilter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakJwtFilter implements IngressRequestFilter {

  private final JsonWebTokenParser jsonWebTokenParser;

  /**
   * When a module (for example mod-circulation) registers events in mod-pubsub the request is sent without
   * x-okapi-token (as we don't pass it during tenant install), mod-pubsub-client lib adds a default "dummy" token to
   * such requests and therefore sidecar should be able to process such request.
   *
   * @param rc - routing context
   * @return {@link Optional} of {@link JsonWebToken} object
   */
  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    return getParsedSystemToken(rc).isPresent()
      ? authenticateRequestWithSystemJwt(rc)
      : authenticateRequest(rc);
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return isSystemRequest(rc) || hasNoPermissionsRequired(rc);
  }

  @Override
  public int getOrder() {
    return 120;
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
    var request = rc.request();
    var headers = request.headers();
    var okapiToken = headers.get(TOKEN);
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
    try {
      var jsonWebToken = jsonWebTokenParser.parse(accessToken);
      return succeededFuture(populateContextAndHeaders(rc, jsonWebToken));
    } catch (ParseException exception) {
      return failedFuture(new UnauthorizedException("Failed to parse JWT", exception));
    }
  }

  private Future<RoutingContext> findAccessTokenWhenSystemJwtPresent(String accessToken, RoutingContext rc) {
    try {
      var jsonWebToken = jsonWebTokenParser.parse(accessToken);
      return succeededFuture(populateContextAndHeaders(rc, jsonWebToken));
    } catch (ParseException exception) {
      return handleParseExceptionForPresentSystemJwt(rc, exception);
    }
  }

  private static Future<RoutingContext> handleParseExceptionForPresentSystemJwt(RoutingContext rc, ParseException err) {
    if (Objects.equals(INVALID_SEGMENTS_JWT_ERROR_MSG, err.getMessage())) {
      return succeededFuture(rc);
    }

    return failedFuture(new UnauthorizedException("Failed to parse JWT", err));
  }

  private static RoutingContext populateContextAndHeaders(RoutingContext rc, JsonWebToken parsedToken) {
    putParsedToken(rc, parsedToken);
    if (!hasUserIdHeader(rc)) {
      getUserIdClaim(parsedToken).ifPresent(userId -> setUserIdHeader(rc, userId));
    }

    return rc;
  }
}
