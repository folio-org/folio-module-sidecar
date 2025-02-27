package org.folio.sidecar.service;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class ModulePermissionsService {

  private volatile Set<String> permissionsCache = Set.of(); // NOSONAR

  public Future<Set<String>> getPermissions() {
    log.debug("Returning permissions from cache: {}", permissionsCache);
    return succeededFuture(permissionsCache);
  }

  public Future<Void> putPermissions(Set<String> permissions) {
    log.debug("Updating permissions cache: {}", permissions);
    permissionsCache = Set.copyOf(permissions);
    return succeededFuture();
  }
}
