package org.folio.sidecar.utils;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.utils.FutureUtils.executeAndGet;
import static org.folio.sidecar.utils.FutureUtils.tryRecoverFrom;

import io.vertx.core.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class FutureUtilsTest {

  @Test
  void executeAndGet_positive() {
    var future = succeededFuture("success");
    var result = executeAndGet(future, Throwable::getMessage);
    assertThat(result).isEqualTo("success");
  }

  @Test
  void executeAndGet_negative() {
    var future = failedFuture(new RuntimeException("failure"));
    var result = (String) executeAndGet(future, Throwable::getMessage);
    assertThat(result).contains("failure");
  }

  @Test
  void executeAndGet_withTimeout_positive() {
    var future = succeededFuture("success");
    var result = executeAndGet(future, Throwable::getMessage, 5, TimeUnit.SECONDS);
    assertThat(result).isEqualTo("success");
  }

  @Test
  void executeAndGet_withTimeout_negative() {
    var future = failedFuture(new RuntimeException("failure"));
    var result = (String) executeAndGet(future, Throwable::getMessage, 5, TimeUnit.SECONDS);
    assertThat(result).contains("failure");
  }

  @Test
  void tryRecoverFrom_positive() {
    Function<Throwable, Future<String>> recovery =
      tryRecoverFrom(IllegalArgumentException.class, ex -> succeededFuture("recovered"));

    var result = recovery.apply(new IllegalArgumentException("failure"));

    assertThat(result.result()).isEqualTo("recovered");
  }

  @Test
  void tryRecoverFrom_negative() {
    Function<Throwable, Future<String>> recovery =
      tryRecoverFrom(IllegalArgumentException.class, ex -> succeededFuture("recovered"));

    var result = recovery.apply(new RuntimeException("failure"));

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isInstanceOf(RuntimeException.class);
  }
}
