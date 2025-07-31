package org.folio.sidecar.integration.te;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.function.UnaryOperator;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.integration.te.model.EntitlementList;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.JsonConverter;

@Log4j2
@ApplicationScoped
public class TenantEntitlementClient {

  private final WebClient webClient;
  private final JsonConverter jsonConverter;
  private final TenantEntitlementClientProperties clientProperties;

  public TenantEntitlementClient(@Named("webClientEgress") WebClient webClient, JsonConverter jsonConverter,
    TenantEntitlementClientProperties clientProperties) {
    validateBatchSizeValue(clientProperties.getBatchSize());
    this.webClient = webClient;
    this.jsonConverter = jsonConverter;
    this.clientProperties = clientProperties;
  }

  /**
   * Provides tenant entitlements.
   *
   * @return {@link Future} of {@link Entitlement} result list
   */
  public Future<ResultList<Entitlement>> getModuleEntitlements(String moduleId, String token) {
    log.info("Loading module entitlements: moduleId = {}", moduleId);

    return requestData(moduleId, token, 0, clientProperties.getBatchSize());
  }

  public Future<ResultList<Entitlement>> getTenantEntitlements(String tenant, boolean withModules, String token) {
    log.info("Loading tenant entitlements: tenant = {}, withModules = {}", tenant, withModules);

    return doGet(entitlementUrl(), token,
      request -> request
        .addQueryParam("tenant", tenant)
        .addQueryParam("limit", Integer.toString(clientProperties.getBatchSize()))
        .addQueryParam("includeModules", Boolean.toString(withModules)))
      .map(response -> jsonConverter.parseResponse(response, EntitlementList.class))
      .map(entitlements -> ResultList.of(entitlements.getTotalRecords(), entitlements.getEntitlements()));
  }

  private Future<ResultList<Entitlement>> requestData(String moduleId, String token, int offset, int batchSize) {
    log.debug("Loading batch of tenant entitlements [offset: {}, limit: {}]", offset, batchSize);

    return doGet(entitlementUrl("/modules/" + moduleId), token,
      request -> request
        .addQueryParam("limit", Integer.toString(batchSize))
        .addQueryParam("offset", Integer.toString(offset)))
      // fix incorrect recursive call below
      .onSuccess(response -> repeatRequestData(moduleId, token, offset, batchSize, response))
      .map(response -> jsonConverter.parseResponse(response, EntitlementList.class))
      .map(entitlements -> ResultList.of(entitlements.getTotalRecords(), entitlements.getEntitlements()));
  }

  private void validateBatchSizeValue(Integer batchSize) {
    if (batchSize == null || batchSize < 1) {
      throw new IllegalArgumentException("Batch size should not be less than 1");
    }
  }

  private void repeatRequestData(String appId, String token, int offset, int batchSize, HttpResponse<Buffer> response) {
    var entitlementList = jsonConverter.parseResponse(response, EntitlementList.class);
    var newOffset = offset + batchSize;
    if (entitlementList.getTotalRecords() > newOffset) {
      requestData(appId, token, newOffset, batchSize);
    }
  }

  private String entitlementUrl() {
    return entitlementUrl(EMPTY);
  }

  private String entitlementUrl(String suffix) {
    return clientProperties.getUrl() + "/entitlements" + suffix;
  }

  private Future<HttpResponse<Buffer>> doGet(String url, String token, UnaryOperator<HttpRequest<Buffer>> customizer) {
    var request = webClient.getAbs(url)
      .putHeader(CONTENT_TYPE, APPLICATION_JSON)
      .putHeader(TOKEN, token);

    return customizer.apply(request).send();
  }
}
