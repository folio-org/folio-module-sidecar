package org.folio.sidecar.integration.te;

import static io.vertx.core.Future.succeededFuture;
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
import java.util.ArrayList;
import java.util.List;
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

    return collectModuleEntitlements(moduleId, token, 0, new ArrayList<>());
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

  /**
   * Loads all entitlements for a tenant, following pagination to completion.
   *
   * @param tenant tenant name
   * @param withModules whether to include the module list per entitlement
   * @param token service token
   * @return future of the full entitlement list across all pages
   */
  public Future<List<Entitlement>> getAllTenantEntitlements(String tenant, boolean withModules, String token) {
    return collectEntitlements(tenant, withModules, token, 0, new ArrayList<>());
  }

  private Future<List<Entitlement>> collectEntitlements(String tenant, boolean withModules, String token,
    int offset, List<Entitlement> accumulator) {
    return doGet(entitlementUrl(), token, request -> request
        .addQueryParam("tenant", tenant)
        .addQueryParam("includeModules", Boolean.toString(withModules))
        .addQueryParam("limit", Integer.toString(clientProperties.getBatchSize()))
        .addQueryParam("offset", Integer.toString(offset)))
      .map(response -> jsonConverter.parseResponse(response, EntitlementList.class))
      .compose(list -> {
        if (list.getEntitlements() != null) {
          accumulator.addAll(list.getEntitlements());
        }
        var next = offset + clientProperties.getBatchSize();
        if (list.getTotalRecords() != null && list.getTotalRecords() > next) {
          return collectEntitlements(tenant, withModules, token, next, accumulator);
        }
        return succeededFuture(accumulator);
      });
  }

  private Future<ResultList<Entitlement>> collectModuleEntitlements(String moduleId, String token,
    int offset, List<Entitlement> accumulator) {
    var batchSize = clientProperties.getBatchSize();
    log.debug("Loading batch of module entitlements [offset: {}, limit: {}]", offset, batchSize);

    return doGet(entitlementUrl("/modules/" + moduleId), token,
      request -> request
        .addQueryParam("limit", Integer.toString(batchSize))
        .addQueryParam("offset", Integer.toString(offset)))
      .map(response -> jsonConverter.parseResponse(response, EntitlementList.class))
      .compose(list -> {
        if (list.getEntitlements() != null) {
          accumulator.addAll(list.getEntitlements());
        }
        var total = list.getTotalRecords();
        var next = offset + batchSize;
        if (total != null && total > next) {
          return collectModuleEntitlements(moduleId, token, next, accumulator);
        }
        return succeededFuture(ResultList.of(total, accumulator));
      });
  }

  private void validateBatchSizeValue(Integer batchSize) {
    if (batchSize == null || batchSize < 1) {
      throw new IllegalArgumentException("Batch size should not be less than 1");
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
