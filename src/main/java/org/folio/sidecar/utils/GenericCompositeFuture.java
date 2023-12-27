package org.folio.sidecar.utils;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.impl.future.CompositeFutureImpl;
import java.util.List;

public interface GenericCompositeFuture {

  /**
   * Return a generic composite future, succeeded when all futures are succeeded, failed when any future is failed.
   *
   * @param futures - {@link java.util.List}
   * @return CompositeFuture {@link io.vertx.core.CompositeFuture} - generic type of CompositeFuture
   */
  static <T extends Future<?>> CompositeFuture all(List<T> futures) {
    return CompositeFutureImpl.all(futures.toArray(new Future[0]));
  }

  /**
   * Return a generic composite future, succeeded when any futures is succeeded, failed when all futures are failed.
   *
   * @param futures - {@link java.util.List}
   * @return CompositeFuture {@link io.vertx.core.CompositeFuture} - generic type of CompositeFuture
   */
  static <T extends Future<?>> CompositeFuture any(List<T> futures) {
    return CompositeFutureImpl.any(futures.toArray(new Future[0]));
  }

  /**
   * Return a generic composite future,succeeded when all futures are succeeded, failed when any future is failed.
   *
   * @param futures - {@link java.util.List}
   * @return CompositeFuture {@link io.vertx.core.CompositeFuture} - generic type of CompositeFuture
   */
  static <T extends Future<?>> CompositeFuture join(List<T> futures) {
    return CompositeFutureImpl.join(futures.toArray(new Future[0]));
  }
}
