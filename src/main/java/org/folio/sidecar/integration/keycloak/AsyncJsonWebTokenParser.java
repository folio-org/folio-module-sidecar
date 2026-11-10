package org.folio.sidecar.integration.keycloak;

import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.jwt.openid.JsonWebTokenParser;

/**
 * Async wrapper for JsonWebTokenParser that offloads CPU-intensive JWT parsing to worker threads to prevent blocking
 * the Vert.x event loop.
 */
@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class AsyncJsonWebTokenParser {

  private final JsonWebTokenParser syncParser;
  private final Vertx vertx;

  /**
   * Parses JWT token asynchronously on a worker thread.
   *
   * @param token the JWT token string to parse
   * @param context ingress context used only for diagnostic correlation
   * @param tokenType access, system or impersonated token
   * @return Future that completes with the parsed JsonWebToken on success
   */
  public Future<JsonWebToken> parseAsync(String token, RoutingContext context, String tokenType) {
    if (!JwtParseLog.isEnabled()) {
      return vertx.executeBlocking(() -> parseToken(token), false);
    }
    var trace = new JwtParseLog(context, tokenType);
    try {
      return vertx.executeBlocking(() -> parseToken(token, trace), false)
        .onComplete(result -> trace.complete(result.cause()));
    } catch (RuntimeException | Error error) {
      trace.complete(error);
      throw error;
    }
  }

  private JsonWebToken parseToken(String token, JwtParseLog trace) {
    trace.workerStarted();
    try {
      var parsedToken = parseToken(token);
      trace.workerCompleted(null);
      return parsedToken;
    } catch (RuntimeException | Error error) {
      trace.workerCompleted(error);
      throw error;
    }
  }

  /**
   * Performs synchronous JWT token parsing with error handling.
   *
   * @param token the JWT token string to parse
   * @return the parsed JsonWebToken
   * @throws UnauthorizedException if parsing fails
   */
  private JsonWebToken parseToken(String token) {
    try {
      log.debug("Parsing JWT token on worker thread");
      return syncParser.parse(token);
    } catch (ParseException e) {
      log.warn("Failed to parse JWT token: {}", e.getMessage());
      throw new UnauthorizedException("Failed to parse JWT", e);
    } catch (RuntimeException e) {
      log.warn("Failed to parse JWT token due to unexpected error", e);
      throw new UnauthorizedException("Failed to parse JWT", e);
    }
  }
}
