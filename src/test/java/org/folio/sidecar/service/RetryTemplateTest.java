package org.folio.sidecar.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.sidecar.configuration.properties.RetryProperties;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class RetryTemplateTest {

  private RetryTemplate retryTemplate;
  private RetryProperties retryProperties;

  @BeforeEach
  void setup() {
    retryProperties = new RetryProperties();
    retryProperties.setAttempts(3);
    retryProperties.setBackOffFactor(1);
    retryProperties.setMinDelay(Duration.ofMillis(1));
    retryProperties.setMaxDelay(Duration.ofMillis(10));
    retryTemplate = new RetryTemplate(retryProperties);
  }

  @Test
  void callAsync_positive() {
    var resultFuture = retryTemplate.callAsync(() -> succeededFuture("success"));
    var result = resultFuture.toCompletionStage().toCompletableFuture().join();

    assertThat(result).isEqualTo("success");
  }

  @Test
  void callAsync_positive_retryThenSuccess() {
    var counter = new AtomicInteger(0);
    var resultFuture = retryTemplate.callAsync(() -> {
      if (counter.getAndIncrement() < 2) {
        return failedFuture(new RuntimeException("fail"));
      } else {
        return succeededFuture("success");
      }
    });

    var result = resultFuture.toCompletionStage().toCompletableFuture().join();

    assertThat(result).isEqualTo("success");
    assertThat(counter.get()).isEqualTo(3);
  }

  @Test
  void callAsync_negative_illegalArgException() {
    var counter = new AtomicInteger(0);
    var resultFuture = retryTemplate.callAsync(() -> {
      counter.incrementAndGet();
      return failedFuture(new IllegalArgumentException("abort"));
    });

    var exp = assertThrowsExactly(CompletionException.class,
      () -> resultFuture.toCompletionStage().toCompletableFuture().join());
    assertThat(exp.getCause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void callAsync_negative_toMuchRetries() {
    var counter = new AtomicInteger(0);
    var resultFuture = retryTemplate.callAsync(() -> {
      counter.incrementAndGet();
      return failedFuture(new RuntimeException("fail"));
    });
    var exp = assertThrowsExactly(CompletionException.class, () ->
      resultFuture.toCompletionStage().toCompletableFuture().join());
    assertThat(exp.getCause()).isInstanceOf(RuntimeException.class);
    assertThat(counter.get()).isEqualTo(retryProperties.getAttempts() + 1);
  }
}
