package org.folio.sidecar.service;

import static io.vertx.core.Future.fromCompletionStage;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;

import io.smallrye.faulttolerance.api.FaultTolerance;
import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.ResolutionException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.configuration.properties.RetryProperties;

@ApplicationScoped
@RequiredArgsConstructor
public class RetryTemplate {

  private static final int TOTAL_DURATION = 60;

  private final RetryProperties retryProperties;

  public <T> Future<T> callAsync(Supplier<Future<T>> futureSupplier) {
    FaultTolerance<CompletionStage<T>> retry = buildFaultTolerance();

    return fromCompletionStage(retry.get(() -> futureSupplier.get().toCompletionStage()));
  }

  private <T> FaultTolerance<CompletionStage<T>> buildFaultTolerance() {
    long minDelayMillis = retryProperties.getMinDelay().toMillis();
    long maxDelayMillis = retryProperties.getMaxDelay().toMillis();
    return FaultTolerance.<T>createAsync()
      .withRetry()
      .delay(minDelayMillis, MILLIS)
      .maxRetries(retryProperties.getAttempts())
      .maxDuration(TOTAL_DURATION, MINUTES)
      .withExponentialBackoff()
      .factor(retryProperties.getBackOffFactor())
      .maxDelay(maxDelayMillis, MILLIS).done()
      // since quarkus CDI loads all bean lazily, it's possible that some problems appear on first attempt to call
      // a service which can happen inside a retry template execution
      .abortOn(
        List.of(IllegalArgumentException.class, NoSuchElementException.class, ResolutionException.class))
      .done()
      .build();
  }
}
