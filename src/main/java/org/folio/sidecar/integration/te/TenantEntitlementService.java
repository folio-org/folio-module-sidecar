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

  /**
   * Loads tenant entitlements with the shared retry policy, for startup and bootstrap paths.
   *
   * @param tenant - tenant name
   * @param withModules - whether module ids must be included
   * @return future with the tenant entitlements
   */
  public Future<ResultList<Entitlement>> getTenantEntitlements(String tenant, boolean withModules) {
    return callWithRetry(token -> tenantEntitlementClient.getTenantEntitlements(tenant, withModules, token));
  }

  /**
   * Loads tenant entitlements with modules in a single attempt, for request-path lookups.
   *
   * <p>The shared retry policy is deliberately not applied here: it can hold a request for minutes, and a
   * lookup failure is recovered by the next request instead.</p>
   *
   * @param tenant - tenant name
   * @return future with the tenant entitlements
   */
  public Future<ResultList<Entitlement>> lookupTenantEntitlements(String tenant) {
    return tokenProvider.getAdminToken()
      .compose(token -> tenantEntitlementClient.getTenantEntitlements(tenant, true, token));
  }

  private <T> Future<T> callWithRetry(Function<String, Future<T>> apiCall) {
    return retryTemplate.callAsync(() -> tokenProvider.getAdminToken().compose(apiCall));
  }
}
