package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.utils.RoutingUtils.isSelfRequest;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.exception.TenantNotEnabledException;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.utils.RoutingUtils;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TenantFilter implements IngressRequestFilter {

  private final TenantService tenantService;

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    var tenant = rc.request().headers().get(OkapiHeaders.TENANT);
    return tenantService.isEnabledTenant(tenant)
      ? succeededFuture(rc)
      : failedFuture(new TenantNotEnabledException(tenant));
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return RoutingUtils.isTenantInstallRequest(rc) || isSelfRequest(rc);
  }

  @Override
  public int getOrder() {
    return 140;
  }
}
