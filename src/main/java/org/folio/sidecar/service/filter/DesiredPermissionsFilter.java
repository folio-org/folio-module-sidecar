package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.join;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.PERMISSIONS;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.RoutingUtils.getPermissionsDesired;
import static org.folio.sidecar.utils.RoutingUtils.getTenant;
import static org.folio.sidecar.utils.RoutingUtils.getUserIdHeader;
import static org.folio.sidecar.utils.RoutingUtils.hasPermissionsDesired;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.users.UserService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class DesiredPermissionsFilter implements IngressRequestFilter {

  private final UserService userService;

  @Override
  public int getOrder() {
    return 171;
  }

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    rc.request().headers().remove(PERMISSIONS);
    var userIdHeader = getUserIdHeader(rc);

    if (userIdHeader.isEmpty()) {
      log.info("Skipping population of X-Okapi-Permissions: user ID not found");
      return succeededFuture(rc);
    }

    if (hasPermissionsDesired(rc)) {
      return succeededFuture(userIdHeader.get())
        .flatMap(userId -> userService.findUserPermissions(rc, getPermissionsDesired(rc), userId, getTenant(rc)))
        .flatMap(permissions -> succeededFuture(populatePermissions(rc, permissions)))
        .otherwise(error -> {
          log.warn("Error occurred while searching user permissions: user = {}, tenant = {}", userIdHeader.get(),
            getTenant(rc), error);
          return rc;
        });
    }

    return succeededFuture(rc);
  }

  private static RoutingContext populatePermissions(RoutingContext rc, List<String> permissions) {
    if (isEmpty(permissions)) {
      log.warn("Skipping population of X-Okapi-Permissions: permissions is empty");
      return rc;
    }

    var perms = join(",", permissions);
    rc.request().headers().set(PERMISSIONS, perms);
    log.info("X-Okapi-Permissions populated: permissions = {}", perms);
    return rc;
  }
}
