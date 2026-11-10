package org.folio.sidecar.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.jwt.openid.JsonWebTokenParser;
import org.folio.support.types.UnitTest;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JwtParseDiagnosticsTest {

  @Mock private JsonWebTokenParser syncParser;
  @Mock private JsonWebToken parsedToken;
  @Mock private Vertx vertx;
  @Mock private RoutingContext context;
  @Mock private HttpServerRequest request;

  private final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("org.folio.sidecar.outbound");
  private final List<String> messages = new CopyOnWriteArrayList<>();
  private final List<String> threads = new CopyOnWriteArrayList<>();
  private final CountDownLatch workerCompleted = new CountDownLatch(1);
  private final AtomicLong queuedLogNanos = new AtomicLong();
  private boolean slowQueuedLog;
  private Level previousLevel;
  private final Handler handler = new Handler() {
    @Override
    public void publish(LogRecord record) {
      var message = ((ExtLogRecord) record).getFormattedMessage();
      if (slowQueuedLog && message.contains("phase=queued")) {
        var start = System.nanoTime();
        try {
          Thread.sleep(100);
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(error);
        }
        queuedLogNanos.set(System.nanoTime() - start);
      }
      messages.add(message);
      threads.add(((ExtLogRecord) record).getThreadName());
      if (message.contains("phase=worker-complete")) {
        workerCompleted.countDown();
      }
    }

    @Override
    public void flush() { }

    @Override
    public void close() { }
  };

  @BeforeEach
  void enableLogging() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.FINE);
    logger.addHandler(handler);
  }

  @AfterEach
  void restoreLogging() {
    logger.removeHandler(handler);
    logger.setLevel(previousLevel);
  }

  @Test
  void parseAsync_positive_distinguishesQueueExecutionAndResultDelivery() throws Exception {
    var work = new AtomicReference<Callable<JsonWebToken>>();
    var result = Promise.<JsonWebToken>promise();
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false))).thenAnswer(invocation -> {
      work.set(invocation.getArgument(0));
      return result.future();
    });
    when(syncParser.parse("secret.jwt.token")).thenAnswer(invocation -> {
      Thread.sleep(20);
      return parsedToken;
    });
    when(context.request()).thenReturn(request);
    when(request.getHeader(TENANT)).thenReturn("tenant\nforged");
    when(request.getHeader(REQUEST_ID)).thenReturn("parent\nforged");
    var parser = new AsyncJsonWebTokenParser(syncParser, vertx);

    final var future = parser.parseAsync("secret.jwt.token", context, "system");

    assertThat(messages).singleElement().asString().contains("phase=queued");
    Thread.sleep(20);
    assertThat(work.get().call()).isSameAs(parsedToken);
    assertThat(future.isComplete()).isFalse();
    assertThat(messages).hasSize(3);
    assertThat(messages.get(1)).contains("phase=worker-start", "queue_ms=");
    assertThat(messages.get(2)).contains("phase=worker-complete", "execution_ms=");
    Thread.sleep(20);
    result.complete(parsedToken);
    assertThat(future.result()).isSameAs(parsedToken);
    assertThat(messages).hasSize(4).allSatisfy(message -> assertThat(message)
      .contains("operation=jwt.parse", "call_id=", "token_type=system", "tenant=tenant_forged",
        "parent_id=parent_forged").doesNotContain("secret.jwt.token", "\n"));
    assertThat(messages.get(3)).contains("phase=complete", "queue_ms=", "execution_ms=", "delivery_ms=",
      "error_type=-");
    var callId = messages.getFirst().split("call_id=")[1].split(" ")[0];
    assertThat(messages).allSatisfy(message -> assertThat(message).contains("call_id=" + callId));
    var complete = messages.getLast();
    var queue = metric(complete, "queue_ms");
    var execution = metric(complete, "execution_ms");
    var delivery = metric(complete, "delivery_ms");
    assertThat(List.of(queue, execution, delivery)).allSatisfy(value -> assertThat(value).isGreaterThanOrEqualTo(20));
    assertThat(metric(complete, "elapsed_ms")).isBetween(queue + execution + delivery,
      queue + execution + delivery + 2);
    assertThat(metric(messages.get(1), "queue_ms")).isEqualTo(queue);
    assertThat(metric(messages.get(2), "execution_ms")).isEqualTo(execution);
  }

  @Test
  void parseAsync_negative_parseFailureKeepsTimingsAndOmitsExceptionMessage() throws Exception {
    when(syncParser.parse("secret.jwt.token")).thenThrow(new ParseException("secret parse detail"));
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false))).thenAnswer(invocation -> {
      try {
        return Future.succeededFuture(invocation.<Callable<JsonWebToken>>getArgument(0).call());
      } catch (Exception error) {
        return Future.failedFuture(error);
      }
    });

    var future = new AsyncJsonWebTokenParser(syncParser, vertx).parseAsync("secret.jwt.token", null, "access");

    assertThat(future.cause()).isInstanceOf(UnauthorizedException.class);
    assertThat(messages).hasSize(4).allSatisfy(message -> assertThat(message).doesNotContain("secret"));
    assertThat(messages.get(2)).contains("phase=worker-complete", "error_type=ParseException");
    assertThat(messages.getLast()).contains("phase=complete", "error_type=ParseException");
    assertThat(metric(messages.getLast(), "execution_ms")).isGreaterThanOrEqualTo(0);
  }

  @Test
  void parseAsync_negative_rejectedFutureDoesNotInventWorkerTimings() {
    var error = new RejectedExecutionException("secret rejection detail");
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false))).thenReturn(Future.failedFuture(error));

    var future = new AsyncJsonWebTokenParser(syncParser, vertx).parseAsync("secret.jwt.token", null, "access");

    assertThat(future.cause()).isSameAs(error);
    assertThat(messages).hasSize(2);
    assertThat(messages.getLast()).contains("phase=complete", "queue_ms=-1", "execution_ms=-1", "delivery_ms=-1",
      "error_type=RejectedExecutionException").doesNotContain("secret");
    verifyNoInteractions(syncParser);
  }

  @Test
  void parseAsync_negative_synchronousSubmissionFailureIsPreserved() {
    var error = new RejectedExecutionException("secret rejection detail");
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false))).thenThrow(error);
    var parser = new AsyncJsonWebTokenParser(syncParser, vertx);

    assertThatThrownBy(() -> parser.parseAsync("secret.jwt.token", null, "access")).isSameAs(error);

    assertThat(messages).hasSize(2);
    assertThat(messages.getLast()).contains("phase=complete", "execution_ms=-1",
      "error_type=RejectedExecutionException").doesNotContain("secret");
    verifyNoInteractions(syncParser);
  }

  @Test
  void parseAsync_positive_disabledKeepsOriginalFutureWithoutReadingContext() {
    logger.setLevel(Level.INFO);
    var result = Promise.<JsonWebToken>promise();
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false))).thenReturn(result.future());

    var future = new AsyncJsonWebTokenParser(syncParser, vertx).parseAsync("secret.jwt.token", context, "access");

    assertThat(future).isSameAs(result.future());
    assertThat(messages).isEmpty();
    verifyNoInteractions(context, syncParser);
  }

  @Test
  void parseAsync_positive_excludesQueuedLogWriteFromTimings() throws Exception {
    slowQueuedLog = true;
    when(syncParser.parse("secret.jwt.token")).thenReturn(parsedToken);
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false))).thenAnswer(invocation ->
      Future.succeededFuture(invocation.<Callable<JsonWebToken>>getArgument(0).call()));
    var parser = new AsyncJsonWebTokenParser(syncParser, vertx);
    var before = System.nanoTime();

    var result = parser.parseAsync("secret.jwt.token", null, "access");

    var wallNanos = System.nanoTime() - before;
    assertThat(result.result()).isSameAs(parsedToken);
    var outsideQueuedLog = TimeUnit.NANOSECONDS.toMillis(wallNanos - queuedLogNanos.get());
    assertThat(metric(messages.getLast(), "elapsed_ms")).isLessThanOrEqualTo(outsideQueuedLog);
    assertThat(metric(messages.getLast(), "queue_ms")).isLessThanOrEqualTo(outsideQueuedLog);
  }

  @Test
  void parseAsync_negative_jvmErrorRetainsExecutionTiming() throws Exception {
    var error = new NoClassDefFoundError("secret class detail");
    when(syncParser.parse("secret.jwt.token")).thenThrow(error);
    when(vertx.<JsonWebToken>executeBlocking(any(Callable.class), eq(false))).thenAnswer(invocation -> {
      try {
        return Future.succeededFuture(invocation.<Callable<JsonWebToken>>getArgument(0).call());
      } catch (Throwable failure) {
        return Future.failedFuture(failure);
      }
    });

    var result = new AsyncJsonWebTokenParser(syncParser, vertx).parseAsync("secret.jwt.token", null, "access");

    assertThat(result.cause()).isSameAs(error);
    assertThat(messages).hasSize(4).allSatisfy(message -> assertThat(message).doesNotContain("secret"));
    assertThat(messages.getLast()).contains("error_type=NoClassDefFoundError");
    assertThat(metric(messages.getLast(), "execution_ms")).isGreaterThanOrEqualTo(0);
  }

  @Test
  void parseAsync_positive_realWorkerQueueAndEventLoopDelivery() throws Exception {
    var actualVertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1).setEventLoopPoolSize(1));
    var occupied = new CountDownLatch(1);
    var releaseWorker = new CountDownLatch(1);
    var releaseEventLoop = new CountDownLatch(1);
    var submitted = new CountDownLatch(1);
    var result = new CompletableFuture<JsonWebToken>();
    var eventLoopThread = new AtomicReference<Thread>();
    var parsingThread = new AtomicReference<Thread>();
    when(syncParser.parse("secret.jwt.token")).thenAnswer(invocation -> {
      parsingThread.set(Thread.currentThread());
      return parsedToken;
    });
    try {
      actualVertx.executeBlocking(() -> {
        occupied.countDown();
        if (!releaseWorker.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Worker was not released");
        }
        return null;
      }, false);
      assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
      actualVertx.runOnContext(ignored -> {
        eventLoopThread.set(Thread.currentThread());
        new AsyncJsonWebTokenParser(syncParser, actualVertx).parseAsync("secret.jwt.token", null, "access")
          .onComplete(parsed -> {
            if (parsed.succeeded()) {
              result.complete(parsed.result());
            } else {
              result.completeExceptionally(parsed.cause());
            }
          });
        submitted.countDown();
        try {
          if (!releaseEventLoop.await(5, TimeUnit.SECONDS)) {
            result.completeExceptionally(new IllegalStateException("Event loop was not released"));
          }
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          result.completeExceptionally(error);
        }
      });
      assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(20);
      releaseWorker.countDown();
      assertThat(workerCompleted.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(20);
      releaseEventLoop.countDown();
      assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(parsedToken);
      assertThat(parsingThread.get()).isNotSameAs(eventLoopThread.get());
      assertThat(messages).hasSize(4);
      assertThat(threads.get(1)).isEqualTo(parsingThread.get().getName());
      assertThat(threads.get(2)).isEqualTo(parsingThread.get().getName());
      assertThat(threads.get(3)).isEqualTo(eventLoopThread.get().getName());
      assertThat(metric(messages.getLast(), "queue_ms")).isGreaterThanOrEqualTo(20);
      assertThat(metric(messages.getLast(), "delivery_ms")).isGreaterThanOrEqualTo(20);
    } finally {
      releaseWorker.countDown();
      releaseEventLoop.countDown();
      actualVertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
  }

  private static long metric(String record, String name) {
    return Long.parseLong(record.split(name + "=")[1].split(" ")[0]);
  }
}
