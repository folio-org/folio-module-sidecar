package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.PERMISSIONS;
import static org.folio.sidecar.service.filter.IngressFilterOrder.DESIRED_PERMISSIONS;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.PermissionsUtils.mergePermissions;
import static org.folio.sidecar.utils.PermissionsUtils.parsePermissionsHeader;
import static org.folio.sidecar.utils.PermissionsUtils.putPermissionsHeaderToRequest;
import static org.folio.sidecar.utils.RoutingUtils.getPermissionsDesired;
import static org.folio.sidecar.utils.RoutingUtils.getTenant;
import static org.folio.sidecar.utils.RoutingUtils.getUserIdHeader;
import static org.folio.sidecar.utils.RoutingUtils.hasHeader;
import static org.folio.sidecar.utils.RoutingUtils.hasPermissionsDesired;
import static org.folio.sidecar.utils.RoutingUtils.hasSystemAccessToken;
import static org.folio.sidecar.utils.RoutingUtils.hasUserIdHeader;

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
    return DESIRED_PERMISSIONS.getOrder();
  }

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    if (!hasSystemAccessToken(rc)) {
      rc.request().headers().remove(PERMISSIONS);
    }

    if (hasPermissionsDesired(rc) && hasUserIdHeader(rc)) {
      var userId = getUserIdHeader(rc)
        .orElseThrow(() -> new IllegalStateException("UserId is not present"));

      return succeededFuture(userId)
        .flatMap(id -> fetchUserPermissions(rc, id))
        .flatMap(permissions -> mergePermissionsWithContext(permissions, rc, userId))
        .otherwise(error -> handlePermissionError(rc, userId, error));
    }

    return succeededFuture(rc);
  }

  private Future<List<String>> fetchUserPermissions(RoutingContext rc, String userId) {
    return userService.findUserPermissions(rc, getPermissionsDesired(rc), userId, getTenant(rc));
  }

  private Future<RoutingContext> mergePermissionsWithContext(List<String> permissions, RoutingContext rc,
    String userId) {
    if (hasHeader(rc, PERMISSIONS)) {
      var existingPermissions = parsePermissionsHeader(rc.request().getHeader(PERMISSIONS));
      permissions = mergePermissions(existingPermissions, permissions);
    }
    return succeededFuture(populatePermissions(rc, permissions, userId));
  }

  private RoutingContext populatePermissions(RoutingContext rc, List<String> permissions, String userId) {
    if (isEmpty(permissions)) {
      log.warn("Skipping population of X-Okapi-Permissions: permissions is empty, userId = {}", userId);
      return rc;
    }

    putPermissionsHeaderToRequest(rc, permissions);
    log.info("X-Okapi-Permissions populated: permissions = {}, userId = {}", permissions, userId);
    return rc;
  }

  private RoutingContext handlePermissionError(RoutingContext rc, String userId, Throwable error) {
    log.warn("Error occurred while searching user permissions: userId = {}, tenant = {}", userId, getTenant(rc), error);
    return rc;
  }
}
