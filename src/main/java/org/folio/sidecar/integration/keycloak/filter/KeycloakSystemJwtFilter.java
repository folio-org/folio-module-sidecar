package org.folio.sidecar.integration.keycloak.filter;

import static io.vertx.core.Future.failedFuture;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.SYSTEM_TOKEN;
import static org.folio.sidecar.service.filter.IngressFilterOrder.KEYCLOAK_SYSTEM_JWT;
import static org.folio.sidecar.utils.RoutingUtils.getHeader;
import static org.folio.sidecar.utils.RoutingUtils.hasSystemAccessToken;
import static org.folio.sidecar.utils.RoutingUtils.isSystemRequest;
import static org.folio.sidecar.utils.RoutingUtils.putParsedSystemToken;
import static software.amazon.awssdk.utils.StringUtils.isBlank;

import io.quarkus.security.UnauthorizedException;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.keycloak.AsyncJsonWebTokenParser;
import org.folio.sidecar.service.filter.IngressRequestFilter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakSystemJwtFilter implements IngressRequestFilter {

  private final AsyncJsonWebTokenParser asyncTokenParser;

  /**
   * Finds system token as X-System-Token header and parses it to {@link JsonWebToken} object.
   *
   * @param rc - {@link RoutingContext} object to filter
   * @return {@link Future} of {@link RoutingContext} object
   */
  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    var token = getHeader(rc, SYSTEM_TOKEN);
    if (isBlank(token)) {
      log.warn("System token not found in the request headers, cannot process system JWT");
      return failedFuture(new UnauthorizedException("Failed to process system JWT"));
    }

    return asyncTokenParser.parseAsync(token)
      .map(parsedToken -> putParsedSystemToken(rc, parsedToken))
      .recover(error -> error instanceof UnauthorizedException
        ? failedFuture(error)
        : failedFuture(new UnauthorizedException("Failed to parse system JWT", error)));
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return isSystemRequest(rc) || !hasSystemAccessToken(rc);
  }

  @Override
  public int getOrder() {
    return KEYCLOAK_SYSTEM_JWT.getOrder();
  }
}
