package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.MODULE_HINT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.model.ScRoutingEntry.dynamicRoutingEntry;
import static org.folio.sidecar.utils.CollectionUtils.toStream;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;
import static org.folio.sidecar.utils.RoutingUtils.getHeader;
import static org.folio.sidecar.utils.RoutingUtils.hasHeader;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.ApplicationManagerService;
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

  private final ApplicationManagerService applicationManagerService;
  private final TenantEntitlementService tenantEntitlementService;
  private final Cache<String, ScRoutingEntry> routingEntryCache;

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

    return getCachedOrLookup(moduleHint, path, rc);
  }

  private Future<Optional<ScRoutingEntry>> getCachedOrLookup(String moduleHint, String path, RoutingContext rc) {
    var request = rc.request();
    var cacheKey = cacheKey(path, request.method());

    var entry = routingEntryCache.getIfPresent(cacheKey);
    if (entry != null) {
      log.debug("Dynamic routing entry found in cache: method = {}, uri = {}, moduleHint = {}, entry = {}",
        request::method, dumpUri(rc), () -> moduleHint, () -> entry);
      return succeededFuture(Optional.of(entry));
    }

    log.debug("Searching routing entries for dynamic request: method = {}, uri = {}, moduleHint = {}",
      request::method, dumpUri(rc), () -> moduleHint);

    var discovery = getDiscovery(moduleHint, rc);

    return discovery.map(md -> {
      var re = dynamicRoutingEntry(md.getLocation(), md.getId(),
        new ModuleBootstrapEndpoint(path, request.method().name()));

      routingEntryCache.put(cacheKey, re);
      log.debug("Dynamic routing entry stored in cache: key {}, entry = {}", cacheKey, re);

      return Optional.of(re);
    });
  }

  private Future<ModuleDiscovery> getDiscovery(String moduleHint, RoutingContext rc) {
    return SemverUtils.hasVersion(moduleHint)
      ? applicationManagerService.getModuleDiscovery(moduleHint)
      : tenantEntitlementService.getTenantEntitlements(getHeader(rc, TENANT), true)
        .map(findEntitledModuleByName(moduleHint, getHeader(rc, TENANT)))
        .compose(applicationManagerService::getModuleDiscovery);
  }

  private static String cacheKey(String path, HttpMethod method) {
    return method + "#" + path;
  }

  private static Function<ResultList<Entitlement>, String> findEntitledModuleByName(String moduleName, String tenant) {
    return result -> toStream(result.getRecords())
      .flatMap(r -> toStream(r.getModules()))
      .filter(m -> m.startsWith(moduleName))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("No entitled module found for name: "
        + "moduleName = " + moduleName + ", tenant = " + tenant));
  }
}
