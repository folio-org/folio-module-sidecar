package org.folio.sidecar.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.PERMISSIONS;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.am.model.ModuleBootstrapInterface;

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

  public static Set<String> findAllModulePermissions(ModuleBootstrap moduleBootstrap) {
    var interfaces = moduleBootstrap.getModule().getInterfaces();
    if (isEmpty(interfaces)) {
      return emptySet();
    }
    return interfaces.stream()
      .map(ModuleBootstrapInterface::getEndpoints)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .map(ModuleBootstrapEndpoint::getModulePermissions)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .collect(toSet());
  }
}
