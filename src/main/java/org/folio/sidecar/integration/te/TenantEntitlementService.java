package org.folio.sidecar.integration.te;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.RetryTemplate;
import org.folio.sidecar.service.token.ServiceTokenProvider;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TenantEntitlementService {

  private final ServiceTokenProvider tokenProvider;
  private final RetryTemplate retryTemplate;
  private final TenantEntitlementClient tenantEntitlementClient;

  public Future<ResultList<Entitlement>> getTenantEntitlements(String tenant, boolean withModules) {
    return callWithRetry(token -> tenantEntitlementClient.getTenantEntitlements(tenant, withModules, token));
  }

  private <T> Future<T> callWithRetry(Function<String, Future<T>> apiCall) {
    return retryTemplate.callAsync(() -> tokenProvider.getAdminToken().compose(apiCall));
  }
}
