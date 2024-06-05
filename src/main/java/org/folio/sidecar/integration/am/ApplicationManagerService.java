package org.folio.sidecar.integration.am;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.service.RetryTemplate;
import org.folio.sidecar.service.ServiceTokenProvider;

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
    var intHolder = new Holder<>(0);
    return retryTemplate.callAsync(() -> {
      log.info("Calling ApplicationManagerService.getModuleBootstrap() with retry: {}", intHolder.value);
      intHolder.value = intHolder.value + 1;

      return tokenProvider.getAdminToken().compose(token ->
        client.getModuleBootstrap(moduleId, token));
    });
  }

  private static final class Holder<T> {
    T value;

    Holder(T value) {
      this.value = value;
    }
  }
}
