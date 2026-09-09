package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.fromCompletionStage;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.sidecar.service.routing.lookup.RoutingLookupUtils.selectEntitledModuleId;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.CacheSettings;
import org.folio.sidecar.configuration.properties.ModuleBindingProperties;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.utils.SemverUtils;

/**
 * Resolves which version of a module a tenant is entitled to, and where that version is deployed.
 *
 * <p>Two caches are kept apart on purpose. The tenant binding answers "which module id", and is corrected
 * immediately by entitlement events. The module discovery answers "at which address", is shared by all tenants on
 * that version, and is corrected by discovery events. Both expire, so a binding that no event ever corrects
 * cannot stay wrong indefinitely.</p>
 */
@Log4j2
@ApplicationScoped
public class TenantModuleResolver {

  private final TenantEntitlementService tenantEntitlementService;
  private final AsyncLoadingCache<TenantModuleKey, String> bindings;
  private final AsyncLoadingCache<String, ModuleDiscovery> discoveries;

  @Inject
  public TenantModuleResolver(TenantEntitlementService tenantEntitlementService,
    @Named("dynamicRoutingDiscoveryCache") AsyncLoadingCache<String, ModuleDiscovery> discoveries,
    ModuleBindingProperties properties) {
    this(tenantEntitlementService, discoveries, properties.cache(), Ticker.systemTicker());
  }

  TenantModuleResolver(TenantEntitlementService tenantEntitlementService,
    AsyncLoadingCache<String, ModuleDiscovery> discoveries, CacheSettings settings, Ticker ticker) {
    this.tenantEntitlementService = tenantEntitlementService;
    this.discoveries = discoveries;
    this.bindings = buildBindingCache(settings, ticker);
  }

  /**
   * Resolves the module version the tenant is entitled to and its current address.
   *
   * @param tenant - tenant name
   * @param moduleName - module name without a version
   * @return future with the resolved module discovery
   */
  public Future<ModuleDiscovery> resolve(String tenant, String moduleName) {
    if (isBlank(tenant)) {
      return failedFuture(new IllegalArgumentException("Tenant is required to resolve module: " + moduleName));
    }

    var key = new TenantModuleKey(tenant, moduleName);
    var resolved = bindings.get(key).thenCompose(discoveries::get);
    return toCallerContext(resolved);
  }

  /**
   * Applies a module identity change carried by an entitlement event.
   *
   * <p>The module id from the event is written directly rather than re-read from mgr-tenant-entitlements: module
   * events are published before the application entitlement is swapped, so re-reading at this moment would return
   * the previous version. REVOKE is ignored for the same reason — during an upgrade it arrives before the new
   * version is entitled, and acting on it would restore exactly the version being replaced.</p>
   *
   * @param event - tenant entitlement event
   */
  public void onEntitlementEvent(TenantEntitlementEvent event) {
    var moduleId = event.getModuleId();
    if (event.getType() == Type.REVOKE || moduleId == null || !SemverUtils.hasVersion(moduleId)) {
      return;
    }

    var key = new TenantModuleKey(event.getTenantName(), SemverUtils.getName(moduleId));
    var updated = bindings.asMap().computeIfPresent(key, (k, current) -> completedFuture(moduleId));
    if (updated == null) {
      log.debug("Entitlement event ignored, module is not resolved for the tenant yet: {}", event);
    }
  }

  /**
   * Drops bindings of tenants this sidecar no longer serves.
   *
   * <p>Module discoveries are left alone: they are shared with the tenants that remain.</p>
   *
   * @param event - event carrying the tenants currently served
   */
  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void onEntitlementsChanged(EntitlementsEvent event) {
    var served = event.getTenants() == null ? Set.<String>of() : event.getTenants();
    bindings.asMap().keySet().removeIf(key -> !served.contains(key.tenant()));
  }

  private AsyncLoadingCache<TenantModuleKey, String> buildBindingCache(CacheSettings settings, Ticker ticker) {
    var builder = Caffeine.newBuilder().ticker(ticker);

    settings.initialCapacity().ifPresent(builder::initialCapacity);
    settings.maxSize().ifPresent(builder::maximumSize);
    settings.expireAfterWrite().ifPresent(duration -> builder.expireAfterWrite(duration.duration(), duration.unit()));

    return builder.buildAsync((key, executor) -> loadBinding(key).toCompletionStage().toCompletableFuture());
  }

  private Future<String> loadBinding(TenantModuleKey key) {
    return tenantEntitlementService.lookupTenantEntitlements(key.tenant())
      .map(entitlements -> selectEntitledModuleId(entitlements, key.moduleName(), key.tenant()));
  }

  private static <T> Future<T> toCallerContext(CompletableFuture<T> future) {
    var context = Vertx.currentContext();
    var result = context == null ? fromCompletionStage(future) : fromCompletionStage(future, context);
    return result.recover(error -> failedFuture(unwrap(error)));
  }

  /** Composing cache futures wraps failures, which would otherwise hide the real cause from logs and handlers. */
  private static Throwable unwrap(Throwable error) {
    return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
  }

  record TenantModuleKey(String tenant, String moduleName) {
  }
}
