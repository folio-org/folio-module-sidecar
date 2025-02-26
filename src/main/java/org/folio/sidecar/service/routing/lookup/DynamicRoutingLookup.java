package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.fromCompletionStage;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.MODULE_HINT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.model.ScRoutingEntry.dynamicRoutingEntry;
import static org.folio.sidecar.utils.CollectionUtils.toStream;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;
import static org.folio.sidecar.utils.RoutingUtils.getHeader;
import static org.folio.sidecar.utils.RoutingUtils.hasHeader;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.utils.SemverUtils;

@Log4j2
@RequiredArgsConstructor
public class DynamicRoutingLookup implements RoutingLookup {

  private final TenantEntitlementService tenantEntitlementService;
  private final AsyncLoadingCache<String, ModuleDiscovery> discoveryCache;

  @Override
  public Future<Optional<ScRoutingEntry>> lookupRoute(String path, RoutingContext rc) {
    if (!hasHeader(rc, MODULE_HINT)) {
      log.debug("Request does not contain module hint header: {}. Dynamic routing lookup cannot be performed",
        MODULE_HINT);
      return succeededFuture(Optional.empty());
    }

    var moduleHint = getHeader(rc, MODULE_HINT);
    if (isBlank(moduleHint)) {
      return failedFuture(new IllegalArgumentException("Module hint header is present but empty: " + MODULE_HINT));
    }

    return lookupForModule(moduleHint, path, rc);
  }

  private Future<Optional<ScRoutingEntry>> lookupForModule(String moduleHint, String path, RoutingContext rc) {
    var request = rc.request();
    log.debug("Getting routing entry for dynamic request: method = {}, uri = {}, moduleHint = {}",
      request::method, dumpUri(rc), () -> moduleHint);

    var moduleId = SemverUtils.hasVersion(moduleHint)
      ? succeededFuture(moduleHint)
      : tenantEntitlementService.getTenantEntitlements(getHeader(rc, TENANT), true)
          .map(findEntitledModuleIdByName(moduleHint, getHeader(rc, TENANT)));

    return moduleId.compose(id -> fromCompletionStage(discoveryCache.get(id)))
      .map(discovery -> routingEntryFromDiscovery(discovery, rc, path))
      .map(Optional::of)
      .onSuccess(entry -> log.debug("Dynamic routing entry found: method = {}, uri = {}, entry = {}",
        request::method, dumpUri(rc), () -> entry));
  }

  private static ScRoutingEntry routingEntryFromDiscovery(ModuleDiscovery discovery, RoutingContext rc, String path) {
    return dynamicRoutingEntry(discovery.getLocation(), discovery.getId(),
      new ModuleBootstrapEndpoint(path, rc.request().method().name()));
  }

  private static Function<ResultList<Entitlement>, String> findEntitledModuleIdByName(String moduleName,
    String tenant) {
    return result -> toStream(result.getRecords())
      .flatMap(r -> toStream(r.getModules()))
      .filter(m -> m.startsWith(moduleName))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("No entitled module found for name: "
        + "moduleName = " + moduleName + ", tenant = " + tenant));
  }
}
