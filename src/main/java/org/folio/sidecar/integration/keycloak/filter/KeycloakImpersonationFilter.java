package org.folio.sidecar.integration.keycloak.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.util.Strings.isBlank;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.utils.JwtUtils.extractTokenIssuer;
import static org.folio.sidecar.utils.JwtUtils.getUserIdClaim;
import static org.folio.sidecar.utils.RoutingUtils.getParsedToken;
import static org.folio.sidecar.utils.RoutingUtils.getTenant;
import static org.folio.sidecar.utils.RoutingUtils.putParsedToken;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.keycloak.JsonWebTokenParser;
import org.folio.sidecar.integration.keycloak.KeycloakImpersonationService;
import org.folio.sidecar.integration.users.UserService;
import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.sidecar.service.filter.IngressRequestFilter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakImpersonationFilter implements IngressRequestFilter {

  private final UserService userService;
  private final JsonWebTokenParser jwtParser;
  private final KeycloakImpersonationService impersonationService;
  private final SidecarSignatureService sidecarSignatureService;

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    var token = getParsedToken(routingContext);
    if (token.isEmpty()) {
      return failedFuture("Token not found in request");
    }

    var jwt = token.get();
    var targetTenant = getTenant(routingContext);

    return succeededFuture(getUserIdClaim(jwt))
      .map(userIdOptional -> userIdOptional.orElseThrow(() -> new NotFoundException("User ID not found in token")))
      .flatMap(userId -> userService.findUser(targetTenant, userId, routingContext))
      .flatMap(user -> impersonationService.getUserToken(targetTenant, user.getUsername()))
      .map(impersonated -> populateRoutingContextByAccessToken(routingContext, impersonated.getAccessToken()))
      .otherwise(error -> throwForbiddenException(error, targetTenant));
  }

  @Override
  public boolean shouldSkip(RoutingContext routingContext) {
    if (sidecarSignatureService.isSelfRequest(routingContext)) {
      return true;
    }
    var tenant = getTenant(routingContext);
    var parsedToken = getParsedToken(routingContext);
    if (parsedToken.isEmpty() || isBlank(tenant)) {
      return true;
    }

    var tokenIssuer = extractTokenIssuer(parsedToken.get());
    return tenant.equals(tokenIssuer);
  }

  @Override
  public int getOrder() {
    return 150;
  }

  private RoutingContext populateRoutingContextByAccessToken(RoutingContext rc, String impersonatedUserToken) {
    rc.request().headers().add(TOKEN, impersonatedUserToken);
    try {
      var jsonWebToken = jwtParser.parse(impersonatedUserToken);
      putParsedToken(rc, jsonWebToken);
      return rc;
    } catch (ParseException exception) {
      throw new UnauthorizedException("Failed to parse impersonated JWT", exception);
    }
  }

  private static RoutingContext throwForbiddenException(Throwable error, String targetTenant) {
    throw new ForbiddenException("Access forbidden to target tenant: " + targetTenant, error);
  }
}
