package org.folio.sidecar.service.store;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.folio.tools.store.SecureStore;

@ApplicationScoped
public class AsyncSecureStoreProxy implements AsyncSecureStore {

  private final Vertx vertx;
  private final SecureStore secureStore;

  @Inject
  public AsyncSecureStoreProxy(Vertx vertx, Instance<SecureStore> secureStoreInstance) {
    this(vertx, secureStoreInstance.get());
  }

  public AsyncSecureStoreProxy(Vertx vertx, SecureStore secureStore) {
    this.vertx = vertx;
    this.secureStore = secureStore;
  }

  @Override
  public Future<String> get(String key) {
    return vertx.executeBlocking(() -> secureStore.get(key));
  }

  @Override
  public Future<Void> set(String key, String value) {
    return vertx.executeBlocking(() -> setAsync(key, value));
  }

  private Void setAsync(String key, String value) {
    secureStore.set(key, value);
    return null;
  }
}
