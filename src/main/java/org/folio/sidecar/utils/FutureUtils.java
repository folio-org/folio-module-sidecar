package org.folio.sidecar.utils;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.core.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public class FutureUtils {

  private static final long DEFAULT_TIMEOUT = 10;
  private static final TimeUnit DEFAULT_TIMEOUT_UNIT = SECONDS;

  public static <T> T executeAndGet(Future<T> future) throws Exception {
    return executeAndGet(future, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
  }

  public static <T> T executeAndGet(Future<T> future, long timeout, TimeUnit unit) throws Exception {
    var cf = future.toCompletionStage().toCompletableFuture();
    return cf.get(timeout, unit);
  }

  public static <T> T executeAndGet(Future<T> future, Function<Throwable, T> onFailure) {
    return executeAndGet(future, onFailure, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
  }

  @SuppressWarnings("java:S2142")
  public static <T> T executeAndGet(Future<T> future, Function<Throwable, T> onFailure, long timeout, TimeUnit unit) {
    var cf = future.toCompletionStage().toCompletableFuture();
    try {
      return cf.get(timeout, unit);
    } catch (Exception e) {
      return onFailure.apply(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T, E extends Exception> Function<Throwable, Future<T>> tryRecoverFrom(Class<E> excClass,
    Function<E, Future<T>> excHandler) {
    return throwable -> excClass.isInstance(throwable)
      ? excHandler.apply((E) throwable)
      : Future.failedFuture(throwable);
  }
}
