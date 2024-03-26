package org.folio.sidecar.integration.tm;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.tm.model.Tenant;
import org.folio.sidecar.integration.tm.model.TenantList;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.JsonConverter;
import org.folio.sidecar.utils.GenericCompositeFuture;

@Log4j2
@ApplicationScoped
public class TenantManagerClient {

  private final WebClient webClient;
  private final JsonConverter jsonConverter;
  private final TenantManagerClientProperties clientProperties;

  public TenantManagerClient(@Named("webClientTls") WebClient webClient, JsonConverter jsonConverter,
    TenantManagerClientProperties clientProperties) {
    this.webClient = webClient;
    this.jsonConverter = jsonConverter;
    this.clientProperties = clientProperties;
  }

  /**
   * Provides tenant info.
   *
   * @return {@link io.vertx.core.Future} of {@link Tenant} result list
   */
  public Future<List<Tenant>> getTenantInfo(List<String> tenantIds, String token) {
    log.info("Loading tenants info [tenantIds: {}]", tenantIds);

    var batchSize = clientProperties.getBatchSize();

    var result = partition(tenantIds.size(), batchSize)
      .mapToObj(seq ->
        webClient.getAbs(clientProperties.getUrl() + "/tenants")
          .addQueryParam("query", format("id == %s", getTenantsQuery(tenantIds, batchSize, seq)))
          .addQueryParam("limit", Integer.toString(batchSize))
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .putHeader(TOKEN, token)
          .send()
          .map(response -> jsonConverter.parseResponse(response, TenantList.class))
          .map(tenants -> ResultList.of(tenants.getTotalRecords(), tenants.getTenants()))
      )
      .toList();

    return composeBatches(result);
  }

  private Future<List<Tenant>> composeBatches(List<Future<ResultList<Tenant>>> result) {
    var list = new ArrayList<Tenant>();
    return GenericCompositeFuture.all(result)
      .compose(tenantListFuture -> {
        addFuturesToList(tenantListFuture, list);
        return Future.succeededFuture(list);
      });
  }

  private void addFuturesToList(CompositeFuture compositeFuture, List<Tenant> list) {
    compositeFuture.<ResultList<Tenant>>list()
      .forEach(resultList -> list.addAll(resultList.getRecords()));
  }

  private static String getTenantsQuery(List<String> tenantIds, int batchSize, int sequence) {
    var tenantSubList = tenantIds.subList(sequence * batchSize, Math.min(tenantIds.size(), (sequence + 1) * batchSize));
    return tenantSubList.stream().collect(Collectors.joining("\" or \"", "(\"", "\")"));
  }

  private IntStream partition(int listSize, int batchSize) {
    return IntStream.range(0, (listSize + batchSize - 1) / batchSize);
  }
}
