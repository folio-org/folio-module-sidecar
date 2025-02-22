package org.folio.sidecar.service;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class ModulePermissionsService {

  @SuppressWarnings("java:S3077")
  private volatile List<String> permissionsCache = List.of();

  public Future<List<String>> getPermissions() {
    log.debug("Returning permissions from cache: {}", permissionsCache);
    return succeededFuture(permissionsCache);
  }

  public Future<Void> putPermissions(List<String> permissions) {
    log.debug("Updating permissions cache: {}", permissions);
    permissionsCache = List.copyOf(permissions);
    return succeededFuture();
  }
}
