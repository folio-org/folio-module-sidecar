package org.folio.sidecar.integration.am;

import static io.vertx.core.Future.succeededFuture;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.BootstrapRequest;
import org.folio.sidecar.integration.am.model.EgressBootstrapResult;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapResponse;
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
    log.info("Loading module bootstrap: moduleId = {}", moduleId);

    return doGet(moduleUrl(moduleId), token)
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

  /**
   * Loads scoped egress bootstrap. Returns an empty Optional when the endpoint is not deployed (HTTP 404/405),
   * so the caller can skip scoped egress without failing.
   *
   * @param moduleId       module identifier
   * @param applicationIds list of application identifiers in scope
   * @param token          service token
   * @return {@link Future} of {@link Optional} containing the {@link EgressBootstrapResult}
   */
  public Future<Optional<EgressBootstrapResult>> getModuleBootstrapEgress(String moduleId,
    List<String> applicationIds, String token) {
    log.info("Loading egress bootstrap: moduleId = {}, applicationIds = {}", moduleId, applicationIds);
    return doPost(moduleUrl(moduleId) + "/bootstrap", BootstrapRequest.egress(applicationIds), token)
      .flatMap(response -> {
        if (isEndpointMissing(response)) {
          log.warn("POST /modules/{}/bootstrap unavailable (status {}); skipping scoped egress",
            moduleId, response.statusCode());
          return succeededFuture(Optional.empty());
        }
        var parsed = jsonConverter.parseResponse(response, ModuleBootstrapResponse.class);
        return succeededFuture(Optional.ofNullable(parsed.getEgress()));
      });
  }

  /**
   * Loads ingress bootstrap (this module's own routes). When the bootstrap endpoint is not deployed (HTTP 404/405,
   * i.e. an older mgr-applications), falls back to the legacy {@code GET /modules/{id}} so the sidecar can still
   * start during a rollout where mgr-applications has not yet been upgraded. Any other failure propagates so startup
   * can fail.
   *
   * @param moduleId module identifier
   * @param token    service token
   * @return {@link Future} of {@link ModuleBootstrap}
   */
  public Future<ModuleBootstrap> getModuleBootstrapIngress(String moduleId, String token) {
    log.info("Loading ingress bootstrap: moduleId = {}", moduleId);
    return doPost(moduleUrl(moduleId) + "/bootstrap", BootstrapRequest.ingress(), token)
      .flatMap(response -> {
        if (isEndpointMissing(response)) {
          log.warn("POST /modules/{}/bootstrap unavailable (status {}); falling back to legacy GET /modules/{}",
            moduleId, response.statusCode(), moduleId);
          return getModuleBootstrap(moduleId, token);
        }
        var ingress = jsonConverter.parseResponse(response, ModuleBootstrapResponse.class).getIngress();
        if (ingress == null) {
          // A 200 with no ingress payload (e.g. an AM that returned only egress, or an empty body) would otherwise
          // NPE later in route initialization; fail explicitly so startup fails cleanly via the normal onFailure path.
          return Future.<ModuleBootstrap>failedFuture(new IllegalStateException(
            "Ingress bootstrap response has no ingress payload for module " + moduleId));
        }
        return succeededFuture(ingress);
      })
      .onFailure(error -> log.warn("Failed to retrieve ingress bootstrap: {}", error.getMessage()));
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

  private Future<HttpResponse<Buffer>> doPost(String url, Object body, String token) {
    return webClient.postAbs(url)
      .putHeader(CONTENT_TYPE, APPLICATION_JSON)
      .putHeader(TOKEN, token)
      .sendJson(body);
  }

  private static boolean isEndpointMissing(HttpResponse<Buffer> response) {
    return response.statusCode() == 404 || response.statusCode() == 405;
  }
}
