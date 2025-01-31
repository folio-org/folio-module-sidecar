package org.folio.sidecar.integration.cred.store;

import io.vertx.core.Future;

public interface AsyncSecureStore {

  Future<String> get(String key);

  Future<Void> set(String key, String value);
}
