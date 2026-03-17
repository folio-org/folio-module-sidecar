package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import io.quarkus.security.ForbiddenException;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpStatus;
import org.folio.sidecar.service.TenantService;

@RequiredArgsConstructor
public class ModuleEntitlementHandler implements ChainedHandler {

  private static final String ENTITLEMENTS_PATH = "/entitlements/modules/";

  private final String moduleId;
  private final TenantService tenantService;

  @Override
  public Future<Boolean> handle(RoutingContext rc) {
    var path = rc.request().path();

    if (!path.startsWith(ENTITLEMENTS_PATH) || rc.request().method() != HttpMethod.GET) {
      return succeededFuture(false);
    }
    
    var requestModuleId = path.substring(ENTITLEMENTS_PATH.length());
    if (!moduleId.equals(requestModuleId)) {
      return failedFuture(new ForbiddenException("Module ID does not match"));
    }

    return respondWithEnabledTenants(rc);
  }

  private Future<Boolean> respondWithEnabledTenants(RoutingContext rc) {
    return tenantService.getEnabledTenants().map(tenants -> {
      var json = new JsonArray(List.copyOf(tenants));

      rc.response()
        .setStatusCode(HttpStatus.SC_OK)
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(json.encode());

      return true;
    });
  }
}
