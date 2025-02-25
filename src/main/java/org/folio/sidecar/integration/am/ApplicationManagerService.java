package org.folio.sidecar.integration.am;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
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

  public Future<ModuleDiscovery> getModuleDiscovery(String moduleId) {
    return callWithRetry(token -> client.getModuleDiscovery(moduleId, token));
  }

  private <T> Future<T> callWithRetry(Function<String, Future<T>> apiCall) {
    return retryTemplate.callAsync(() -> tokenProvider.getAdminToken().compose(apiCall));
  }
}
