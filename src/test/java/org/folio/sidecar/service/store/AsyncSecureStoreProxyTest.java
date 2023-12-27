package org.folio.sidecar.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_SECOND;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.enterprise.inject.Instance;
import org.folio.support.types.UnitTest;
import org.folio.tools.store.SecureStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AsyncSecureStoreProxyTest {

  private static final String TEST_KEY = "test_foo";
  private static final String TEST_SECRET = "secret";

  private Vertx vertx = Vertx.vertx();
  @Mock private SecureStore secureStore;
  @Mock private Instance<SecureStore> secureStoreInstance;

  private AsyncSecureStoreProxy proxy;

  @BeforeEach
  void setup() {
    when(secureStoreInstance.get()).thenReturn(secureStore);
    proxy = new AsyncSecureStoreProxy(vertx, secureStoreInstance);
  }

  @Test
  void get_positive() {
    when(secureStore.get(TEST_KEY)).thenReturn(TEST_SECRET);

    var future = proxy.get(TEST_KEY);

    await().atMost(ONE_SECOND).until(() -> future.isComplete());

    assertThat(future.succeeded()).isTrue();
    assertThat(future.result()).isEqualTo(TEST_SECRET);
  }

  @Test
  void get_negative() {
    when(secureStore.get(TEST_KEY)).thenThrow(new RuntimeException("failure"));

    var future = proxy.get(TEST_KEY);

    awaitCompletion(future);

    assertThat(future.succeeded()).isFalse();
    assertThat(future.cause()).isInstanceOf(RuntimeException.class);
  }

  @Test
  void set_positive() {
    var future = proxy.set(TEST_KEY, TEST_SECRET);

    awaitCompletion(future);

    assertThat(future.succeeded()).isTrue();
    verify(secureStore).set(TEST_KEY, TEST_SECRET);
  }

  @Test
  void set_negative() {
    doThrow(new RuntimeException("Failure")).when(secureStore).set(TEST_KEY, TEST_SECRET);

    var future = proxy.set(TEST_KEY, TEST_SECRET);

    awaitCompletion(future);

    assertThat(future.succeeded()).isFalse();
    assertThat(future.cause()).isInstanceOf(RuntimeException.class);
  }

  private static <T> void awaitCompletion(Future<T> future) {
    await().atMost(ONE_SECOND).until(() -> future.isComplete());
  }
}
