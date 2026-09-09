package org.folio.sidecar.integration.am;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.service.RetryTemplate;
import org.folio.sidecar.service.token.ServiceTokenProvider;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class ApplicationManagerService {

  private final ServiceTokenProvider tokenProvider;
  private final RetryTemplate retryTemplate;
  private final ApplicationManagerClient client;
  private final ModuleProperties moduleProperties;

  public Future<ModuleBootstrap> getModuleBootstrap() {
    var moduleId = moduleProperties.getId();
    return callWithRetry(token -> client.getModuleBootstrap(moduleId, token));
  }

  public Future<ModuleBootstrap> getIngressBootstrap() {
    var moduleId = moduleProperties.getId();
    return callWithRetry(token -> client.getIngressBootstrap(moduleId, token));
  }

  public Future<ModuleBootstrap> getEgressBootstrap(List<String> applicationIds) {
    var moduleId = moduleProperties.getId();
    return callWithRetry(token -> client.getEgressBootstrap(moduleId, applicationIds, token));
  }

  public Future<ModuleDiscovery> getModuleDiscovery(String moduleId) {
    return callWithRetry(token -> client.getModuleDiscovery(moduleId, token));
  }

  /**
   * Loads a module discovery in a single attempt, for request-path lookups.
   *
   * <p>The shared retry policy is deliberately not applied here: it can hold a request for minutes, and a
   * lookup failure is recovered by the next request instead.</p>
   *
   * @param moduleId - full module identifier
   * @return future with the module discovery
   */
  public Future<ModuleDiscovery> lookupModuleDiscovery(String moduleId) {
    return tokenProvider.getAdminToken().compose(token -> client.getModuleDiscovery(moduleId, token));
  }

  private <T> Future<T> callWithRetry(Function<String, Future<T>> apiCall) {
    return retryTemplate.callAsync(() -> tokenProvider.getAdminToken().compose(apiCall));
  }
}
