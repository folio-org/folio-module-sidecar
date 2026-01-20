package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.failedFuture;

import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
   * @return Future that completes with the parsed JsonWebToken on success, or fails with UnauthorizedException if
   *   parsing fails
   */
  public Future<JsonWebToken> parseAsync(String token) {
    return vertx.executeBlocking(() -> parseToken(token), false)
      .recover(AsyncJsonWebTokenParser::handleParsingError);
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

  /**
   * Handles errors that occur during async JWT parsing.
   *
   * @param error the error that occurred
   * @return a failed Future with appropriate exception
   */
  private static Future<JsonWebToken> handleParsingError(Throwable error) {
    if (error instanceof UnauthorizedException) {
      return failedFuture(error);
    }

    log.warn("Failed to parse JWT token due to async execution failure", error);
    return failedFuture(new UnauthorizedException("Failed to parse JWT", error));
  }
}
