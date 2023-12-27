package org.folio.sidecar.integration.am;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.service.JsonConverter;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class ApplicationManagerClient {

  private final WebClient webClient;
  private final JsonConverter jsonConverter;
  private final AppManagerClientProperties clientProperties;

  /**
   * Provides module bootstrap information from Application Manager.
   *
   * @return {@link Future} of {@link ModuleBootstrap} object
   */
  public Future<ModuleBootstrap> getModuleBootstrap(String moduleId, String token) {
    log.info("Loading module bootstrap [moduleId: {}]", moduleId);

    return webClient.getAbs(clientProperties.getUrl() + "/modules/" + moduleId)
      .putHeader(CONTENT_TYPE, APPLICATION_JSON)
      .putHeader(TOKEN, token)
      .send()
      .map(response -> jsonConverter.parseResponse(response, ModuleBootstrap.class))
      .onSuccess(mb -> log.debug("Module bootstrapping info loaded: {}", mb))
      .onFailure(error -> log.warn("Failed to retrieve module bootstrap: {}", error.getMessage()));
  }
}
