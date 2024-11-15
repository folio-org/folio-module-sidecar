package org.folio.sidecar.integration.keycloak.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toSet;
import static org.folio.sidecar.service.filter.IngressFilterOrder.KEYCLOAK_TENANT;
import static org.folio.sidecar.utils.RoutingUtils.getParsedSystemToken;
import static org.folio.sidecar.utils.RoutingUtils.getParsedToken;
import static org.folio.sidecar.utils.RoutingUtils.getTenant;
import static org.folio.sidecar.utils.RoutingUtils.hasNoPermissionsRequired;
import static org.folio.sidecar.utils.RoutingUtils.isSelfRequest;
import static org.folio.sidecar.utils.RoutingUtils.isSystemRequest;
import static org.folio.sidecar.utils.RoutingUtils.isTimerRequest;

import io.quarkus.security.UnauthorizedException;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.filter.IngressRequestFilter;
import org.folio.sidecar.utils.JwtUtils;

@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakTenantFilter implements IngressRequestFilter {

  private final SidecarProperties sidecarProperties;

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    var resolvedTenants = resolveTokenTenants(rc);
    if (resolvedTenants.isEmpty()) {
      return failedFuture(new UnauthorizedException("Failed to resolve a tenant from token"));
    }

    if (sidecarProperties.isCrossTenantEnabled()) {
      return Future.succeededFuture(rc);
    }

    if (resolvedTenants.size() > 1) {
      var errorMessage = "Resolved tenant from X-System-Token header is not the same as from X-Okapi-Token";
      return failedFuture(new UnauthorizedException(errorMessage));
    }

    var tenantHeader = getTenant(rc);
    if (tenantHeader != null && !resolvedTenants.contains(tenantHeader)) {
      return failedFuture(new UnauthorizedException("X-Okapi-Tenant header is not the same as resolved tenant"));
    }

    return succeededFuture(rc);
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return !isTimerRequest(rc) && (isSystemRequest(rc) || hasNoPermissionsRequired(rc)) || isSelfRequest(rc);
  }

  @Override
  public int getOrder() {
    return KEYCLOAK_TENANT.getOrder();
  }

  private static Set<String> resolveTokenTenants(RoutingContext rc) {
    return Stream.of(getParsedToken(rc), getParsedSystemToken(rc))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(JwtUtils::getOriginTenant)
      .collect(toSet());
  }
}
