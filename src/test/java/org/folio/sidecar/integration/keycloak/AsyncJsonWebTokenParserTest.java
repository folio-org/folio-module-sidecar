package org.folio.sidecar.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.jwt.openid.JsonWebTokenParser;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AsyncJsonWebTokenParserTest {

  @Mock private JsonWebTokenParser syncParser;
  @Mock private JsonWebToken mockToken;
  @Mock private Vertx vertx;

  private AsyncJsonWebTokenParser asyncParser;

  @BeforeEach
  void setUp() {
    asyncParser = new AsyncJsonWebTokenParser(syncParser, vertx);
  }

  @Test
  void parseAsync_positive_returnsParsedToken() throws Exception {
    var token = "valid.jwt.token";
    when(syncParser.parse(token)).thenReturn(mockToken);
    mockExecuteBlockingCallsCallable();

    var result = asyncParser.parseAsync(token).toCompletionStage().toCompletableFuture().get();

    assertThat(result).isSameAs(mockToken);
  }

  @Test
  void parseAsync_negative_parseExceptionFailsWithUnauthorizedException() throws Exception {
    var token = "invalid.jwt.token";
    when(syncParser.parse(token)).thenThrow(new ParseException("Invalid JWT"));
    mockExecuteBlockingCallsCallable();

    var future = asyncParser.parseAsync(token).toCompletionStage().toCompletableFuture();

    assertThat(future)
      .failsWithin(Duration.ofSeconds(1))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(UnauthorizedException.class)
      .withMessageContaining("Failed to parse JWT");
  }

  @Test
  void parseAsync_negative_runtimeExceptionFailsWithUnauthorizedException() throws Exception {
    var token = "invalid.jwt.token";
    when(syncParser.parse(token)).thenThrow(new IllegalStateException("Boom"));
    mockExecuteBlockingCallsCallable();

    var future = asyncParser.parseAsync(token).toCompletionStage().toCompletableFuture();

    assertThat(future)
      .failsWithin(Duration.ofSeconds(1))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(UnauthorizedException.class)
      .withMessageContaining("Failed to parse JWT");
  }

  @Test
  void parseAsync_negative_executeBlockingFailsWithUnauthorizedException() {
    var token = "invalid.jwt.token";
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false)))
      .thenReturn(Future.failedFuture(new IllegalStateException("Worker pool unavailable")));

    var future = asyncParser.parseAsync(token).toCompletionStage().toCompletableFuture();

    assertThat(future)
      .failsWithin(Duration.ofSeconds(1))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(UnauthorizedException.class)
      .withMessageContaining("Failed to parse JWT");
  }

  private void mockExecuteBlockingCallsCallable() {
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false)))
      .thenAnswer(invocation -> {
        Callable<JsonWebToken> callable = invocation.getArgument(0, Callable.class);
        try {
          return Future.succeededFuture(callable.call());
        } catch (Exception e) {
          return Future.failedFuture(e);
        }
      });
  }
}
