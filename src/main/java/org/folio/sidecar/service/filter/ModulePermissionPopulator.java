package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toSet;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.PermissionsUtils.putPermissionsHeaderToRequest;
import static org.folio.sidecar.utils.RoutingUtils.getScRoutingEntry;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ModulePermissionsService;

@ApplicationScoped
@RequiredArgsConstructor
public class ModulePermissionPopulator implements EgressRequestFilter {

  private final ModulePermissionsService modulePermissionsService;

  @Override
  public int getOrder() {
    return HIGHEST_PRECEDENCE;
  }

  @Override
  public Future<RoutingContext> filter(RoutingContext ctx) {
    if (shouldSkip(ctx)) {
      return succeededFuture(ctx);
    }

    return modulePermissionsService.getPermissions()
      .map(permissions -> findDesiredPermissions(getScRoutingEntry(ctx), permissions))
      .map(permissions -> putPermissionsHeaderToRequest(ctx, new ArrayList<>(permissions)));
  }

  private static boolean shouldSkip(RoutingContext ctx) {
    var scRoutingEntry = getScRoutingEntry(ctx);
    return scRoutingEntry == null
      || scRoutingEntry.getRoutingEntry() == null
      || isEmpty(scRoutingEntry.getRoutingEntry().getPermissionsDesired());
  }

  private static Set<String> findDesiredPermissions(ScRoutingEntry re, Set<String> modulePermissions) {
    var routingEntry = re.getRoutingEntry();
    var preparedForMatching = routingEntry.getPermissionsDesired().stream()
      .map(permission -> permission.replace("*", ""))
      .toList();
    return modulePermissions.stream()
      .filter(permission -> preparedForMatching.stream().anyMatch(permission::startsWith))
      .collect(toSet());
  }
}

