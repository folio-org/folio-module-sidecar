package org.folio.sidecar.service;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class ModulePermissionsService {

  private final AtomicReference<List<String>> permissionsCache = new AtomicReference<>(List.of());

  public Future<List<String>> getPermissions() {
    var permissions = permissionsCache.get();
    log.debug("Returning permissions from cache: {}", permissions);
    return succeededFuture(permissions);
  }

  public Future<Void> putPermissions(List<String> permissions) {
    log.debug("Updating permissions cache: {}", permissions);
    permissionsCache.set(List.copyOf(permissions));
    return succeededFuture();
  }
}
