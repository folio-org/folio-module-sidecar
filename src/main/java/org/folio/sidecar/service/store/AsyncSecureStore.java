package org.folio.sidecar.service.store;

import io.vertx.core.Future;

public interface AsyncSecureStore {

  Future<String> get(String key);

  Future<Void> set(String key, String value);
}
