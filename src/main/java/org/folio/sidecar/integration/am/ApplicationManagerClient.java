package org.folio.sidecar.integration.am;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.service.JsonConverter;

@Log4j2
@ApplicationScoped
public class ApplicationManagerClient {

  private final WebClient webClient;
  private final JsonConverter jsonConverter;
  private final AppManagerClientProperties clientProperties;

  public ApplicationManagerClient(@Named("webClientEgress") WebClient webClient, JsonConverter jsonConverter,
    AppManagerClientProperties clientProperties) {
    this.webClient = webClient;
    this.jsonConverter = jsonConverter;
    this.clientProperties = clientProperties;
  }

  /**
   * Provides module bootstrap information from Application Manager.
   *
   * @return {@link Future} of {@link ModuleBootstrap} object
   */
  public Future<ModuleBootstrap> getModuleBootstrap(String moduleId, String token) {
    return getModuleBootstrap(moduleId, null, token);
  }

  /**
   * Provides module bootstrap information from Application Manager.
   *
   * @param moduleId - module identifier
   * @param applicationId - optional application identifier to scope the bootstrap lookup
   * @param token - okapi token
   * @return {@link Future} of {@link ModuleBootstrap} object
   */
  public Future<ModuleBootstrap> getModuleBootstrap(String moduleId, String applicationId, String token) {
    log.info("Loading module bootstrap: moduleId = {}, applicationId = {}", moduleId, applicationId);

    var url = moduleUrl(moduleId)
      + (applicationId != null ? "?applicationId=" + URLEncoder.encode(applicationId, StandardCharsets.UTF_8) : "");
    return doGet(url, token)
      .map(response -> jsonConverter.parseResponse(response, ModuleBootstrap.class))
      .onSuccess(mb -> log.debug("Module bootstrapping info loaded: {}", mb))
      .onFailure(error -> log.warn("Failed to retrieve module bootstrap: {}", error.getMessage()));
  }

  public Future<ModuleDiscovery> getModuleDiscovery(String moduleId, String token) {
    log.info("Loading module discovery: moduleId = {}", moduleId);

    return doGet(moduleUrl(moduleId) + "/discovery", token)
      .map(response -> jsonConverter.parseResponse(response, ModuleDiscovery.class))
      .onSuccess(md -> log.debug("Module discovery loaded: {}", md))
      .onFailure(error -> log.warn("Failed to retrieve module discovery: {}", error.getMessage()));
  }

  private String moduleUrl(String moduleId) {
    return clientProperties.getUrl() + "/modules/" + moduleId;
  }

  private Future<HttpResponse<Buffer>> doGet(String url, String token) {
    return webClient.getAbs(url)
      .putHeader(CONTENT_TYPE, APPLICATION_JSON)
      .putHeader(TOKEN, token)
      .send();
  }
}
