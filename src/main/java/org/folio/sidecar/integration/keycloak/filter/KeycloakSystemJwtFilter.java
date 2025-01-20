package org.folio.sidecar.integration.keycloak.filter;

import static io.vertx.core.Future.failedFuture;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.SYSTEM_TOKEN;
import static org.folio.sidecar.service.filter.IngressFilterOrder.KEYCLOAK_SYSTEM_JWT;
import static org.folio.sidecar.utils.RoutingUtils.hasSystemAccessToken;
import static org.folio.sidecar.utils.RoutingUtils.isSystemRequest;
import static org.folio.sidecar.utils.RoutingUtils.putParsedSystemToken;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.jwt.openid.JsonWebTokenParser;
import org.folio.sidecar.service.filter.IngressRequestFilter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakSystemJwtFilter implements IngressRequestFilter {

  private final JsonWebTokenParser tokenParser;

  /**
   * Finds system token as X-System-Token header and parses it to {@link JsonWebToken} object.
   *
   * @param rc - {@link RoutingContext} object to filter
   * @return {@link Future} of {@link RoutingContext} object
   */
  @WithSpan
  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    return getSystemToken(rc)
      .map(this::parseJsonWebToken)
      .map(parsedToken -> putParsedSystemToken(rc, parsedToken))
      .map(Future::succeededFuture)
      .orElseGet(() -> failedFuture(new UnauthorizedException("Failed to process system JWT")));
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return isSystemRequest(rc) || !hasSystemAccessToken(rc);
  }

  @Override
  public int getOrder() {
    return KEYCLOAK_SYSTEM_JWT.getOrder();
  }

  private JsonWebToken parseJsonWebToken(String systemAccessToken) {
    try {
      return tokenParser.parse(systemAccessToken);
    } catch (ParseException exception) {
      throw new UnauthorizedException("Failed to parse JWT", exception);
    }
  }

  private Optional<String> getSystemToken(RoutingContext rc) {
    var request = rc.request();
    var headers = request.headers();
    return Optional.ofNullable(headers.get(SYSTEM_TOKEN));
  }
}
