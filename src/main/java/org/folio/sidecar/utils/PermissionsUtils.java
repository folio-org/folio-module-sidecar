package org.folio.sidecar.utils;

import static java.util.Collections.emptyList;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.PERMISSIONS;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.Permission;

@Log4j2
@UtilityClass
public class PermissionsUtils {

  public static RoutingContext putPermissionsHeaderToRequest(RoutingContext rc, List<String> permissions) {
    if (isEmpty(permissions)) {
      return rc;
    }
    var perms = new JsonArray(permissions).encode();
    rc.request().headers().set(PERMISSIONS, perms);
    return rc;
  }

  public static List<String> parsePermissionsHeader(String permissionsHeader) {
    try {
      return new JsonArray(permissionsHeader).getList();
    } catch (Exception e) {
      log.warn("Failed to parse existing permissions header: {}", permissionsHeader, e);
      return emptyList();
    }
  }

  public static List<String> mergePermissions(List<String> existingPermissions, List<String> newPermissions) {
    var mergedPermissions = new HashSet<>(existingPermissions);
    if (newPermissions != null) {
      mergedPermissions.addAll(newPermissions);
    }
    return new ArrayList<>(mergedPermissions);
  }

  public static List<String> extractPermissions(ModuleBootstrap moduleBootstrap) {
    var permissionSets = moduleBootstrap.getModule().getPermissionSets();
    if (isEmpty(permissionSets)) {
      return emptyList();
    }
    return permissionSets.stream().map(Permission::getPermissionName).toList();
  }
}
