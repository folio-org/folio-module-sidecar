package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.service.filter.IngressFilterOrder.TENANT;
import static org.folio.sidecar.utils.RoutingUtils.isSelfRequest;
import static org.folio.sidecar.utils.RoutingUtils.isTenantInstallRequest;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.exception.TenantNotEnabledException;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.service.TenantService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TenantFilter implements IngressRequestFilter {

  private final TenantService tenantService;

  @Override
  public Future<RoutingContext> filter(RoutingContext rc) {
    var tenant = rc.request().headers().get(OkapiHeaders.TENANT);
    return tenantService.isEnabledTenant(tenant)
      .compose(getOrThrow(
        () -> rc,
        () -> new TenantNotEnabledException(tenant))
      )
      .onFailure(exc -> {
        // load tenants and entitlements if tenant is not enabled
        if  (exc instanceof TenantNotEnabledException) {
          tenantService.executeTenantsAndEntitlementsTask();
        }
      });
  }

  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return isTenantInstallRequest(rc) || isSelfRequest(rc);
  }

  @Override
  public int getOrder() {
    return TENANT.getOrder();
  }

  private static <T> Function<Boolean, Future<T>> getOrThrow(Supplier<T> positiveSupplier,
    Supplier<Throwable> excSupplier) {
    return val -> val ? succeededFuture(positiveSupplier.get()) : failedFuture(excSupplier.get());
  }
}
