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

    return requestData(moduleId, token, 0, clientProperties.getBatchSize());
  }

  /**
   * Provides all entitlements of a tenant, loading every page until {@code totalRecords} records are read. A single
   * request is capped by the batch size, so without paging a tenant with more entitlements would be cut off.
   *
   * @param tenant - tenant name
   * @param withModules - whether the module ids must be included
   * @param token - admin token
   * @return {@link Future} of the {@link Entitlement} result list with the records of all pages
   */
  public Future<ResultList<Entitlement>> getTenantEntitlements(String tenant, boolean withModules, String token) {
    log.info("Loading tenant entitlements: tenant = {}, withModules = {}", tenant, withModules);

    return loadTenantEntitlements(tenant, withModules, token, 0, new ArrayList<>());
  }

  /**
   * Loads the page that starts at the offset, then continues with {@code nextPageOrResult}.
   *
   * @param offset - number of records loaded so far
   * @param loaded - the records of the pages loaded so far
   */
  private Future<ResultList<Entitlement>> loadTenantEntitlements(String tenant, boolean withModules, String token,
    int offset, List<Entitlement> loaded) {
    return doGet(entitlementUrl(), token,
      request -> request
        .addQueryParam("tenant", tenant)
        .addQueryParam("limit", Integer.toString(clientProperties.getBatchSize()))
        .addQueryParam("offset", Integer.toString(offset))
        .addQueryParam("includeModules", Boolean.toString(withModules)))
      .map(response -> jsonConverter.parseResponse(response, EntitlementList.class))
      .compose(page -> nextPageOrResult(tenant, withModules, token, page, loaded));
  }

  /**
   * Adds the page to the loaded records and decides whether to continue. Loading stops when the total is read or
   * a page is empty, so a total that the server over-reports cannot cause an endless loop.
   *
   * @param page - the page just read
   * @param loaded - the records of the pages loaded so far, extended with this page
   */
  private Future<ResultList<Entitlement>> nextPageOrResult(String tenant, boolean withModules, String token,
    EntitlementList page, List<Entitlement> loaded) {
    var records = page.getEntitlements() == null ? List.<Entitlement>of() : page.getEntitlements();
    loaded.addAll(records);
    var total = page.getTotalRecords() == null ? loaded.size() : page.getTotalRecords();
    if (records.isEmpty() || loaded.size() >= total) {
      return succeededFuture(ResultList.of(total, loaded));
    }
    return loadTenantEntitlements(tenant, withModules, token, loaded.size(), loaded);
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
