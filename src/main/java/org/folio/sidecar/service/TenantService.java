package org.folio.sidecar.service;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import io.quarkus.scheduler.Scheduled;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.te.TenantEntitlementClient;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.integration.tm.TenantManagerClient;
import org.folio.sidecar.integration.tm.model.Tenant;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.token.ServiceTokenProvider;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TenantService {

  @ConfigProperty(name = "tenant-service.reset-task.cron-definition")
  String resetTaskCronDefinition;

  private final ServiceTokenProvider tokenProvider;
  private final RetryTemplate retryTemplate;
  private final TenantManagerClient tenantManagerClient;
  private final TenantEntitlementClient tenantEntitlementClient;
  private final ModuleProperties moduleProperties;
  private final EventBus eventBus;

  /**
   * Maps tenant name → set of applicationIds that have entitled this tenant.
   * A tenant is considered enabled if and only if its set is non-empty.
   */
  private final Map<String, Set<String>> tenantApplications = new ConcurrentHashMap<>();

  private final AtomicBoolean canExecuteTenantsAndEntitlementsTask = new AtomicBoolean(false);

  private Promise<Void> initPromise = Promise.promise();
  private Future<Void> loadingFuture = initPromise.future();

  public Future<Void> init() {
    log.info("Effective cron for tenant entitlements reset-task: {}", resetTaskCronDefinition);

    loadTenantsAndEntitlements(initPromise);

    var result = initPromise.future().onSuccess(unused ->
      log.info("Successfully initialized tenant entitlements for module: {}", moduleProperties.getId()));

    initPromise = null; // once utilized the initialization promise is no longer needed

    return result;
  }

  /**
   * Enables a tenant for the given applicationId.
   * A tenant remains enabled as long as at least one applicationId is registered for it.
   * When applicationId is null (legacy events without applicationId) the call is a no-op.
   */
  public void enableTenant(String tenantName, String applicationId) {
    if (applicationId == null) {
      log.warn("enableTenant called with null applicationId for tenant: {}; ignoring", tenantName);
      return;
    }
    var apps = tenantApplications.computeIfAbsent(tenantName, k -> ConcurrentHashMap.newKeySet());
    if (apps.add(applicationId)) {
      log.info("Enabling tenant: {} for application: {}", tenantName, applicationId);
      notifyEntitlementsChanged();
    }
  }

  /**
   * Disables a tenant for the given applicationId.
   * If the tenant has no more registered applicationIds it is fully disabled.
   * When applicationId is null (legacy events without applicationId) the call is a no-op.
   */
  public void disableTenant(String tenantName, String applicationId) {
    if (applicationId == null) {
      log.warn("disableTenant called with null applicationId for tenant: {}; ignoring", tenantName);
      return;
    }
    var apps = tenantApplications.get(tenantName);
    if (apps == null) {
      return;
    }
    if (apps.remove(applicationId)) {
      log.info("Disabling tenant: {} for application: {}", tenantName, applicationId);
      if (apps.isEmpty()) {
        tenantApplications.remove(tenantName);
        log.info("Tenant {} fully disabled (no more applications)", tenantName);
      }
      notifyEntitlementsChanged();
    }
  }

  /**
   * Returns the set of applicationIds registered for the given tenant.
   */
  public Set<String> getApplicationIds(String tenantName) {
    if (tenantName == null) {
      return Collections.emptySet();
    }
    var apps = tenantApplications.get(tenantName);
    return apps == null ? Collections.emptySet() : Set.copyOf(apps);
  }

  /**
   * Returns all applicationIds across all enabled tenants.
   */
  public Set<String> getAllApplicationIds() {
    return tenantApplications.values().stream()
      .flatMap(Set::stream)
      .collect(Collectors.toUnmodifiableSet());
  }

  public boolean isAssignedModule(String moduleId) {
    return Objects.equals(moduleProperties.getId(), moduleId);
  }

  public Future<Set<String>> getEnabledTenants() {
    return loadingFuture.map(v -> Set.copyOf(tenantApplications.keySet()));
  }

  public Future<Boolean> isEnabledTenant(String name) {
    return isEmpty(name)
      ? succeededFuture(false)
      : loadingFuture.map(v -> tenantApplications.containsKey(name));
  }

  /**
    * This logic is implemented in scope of MODSIDECAR-126.
    * Should be removed after design long term solution.
  */
  public void executeTenantsAndEntitlementsTask() {
    if (canExecuteTenantsAndEntitlementsTask.compareAndSet(true, false)) {
      var promise = Promise.<Void>promise();
      loadingFuture = promise.future();

      loadTenantsAndEntitlements(promise);

      log.info("Task to load tenants and entitlements started");
    }
  }

  @Scheduled(cron = "{tenant-service.reset-task.cron-definition}")
  void resetTaskFlag() {
    if (!canExecuteTenantsAndEntitlementsTask.get()) {
      log.info("Resetting task flag for loading tenants and entitlements to true");
      canExecuteTenantsAndEntitlementsTask.set(true);
    }
  }

  private void loadTenantsAndEntitlements(Promise<Void> promise) {
    var retryFuture = retryTemplate.callAsync(() ->
      tokenProvider.getAdminToken().compose(token ->
        tenantEntitlementClient.getModuleEntitlements(moduleProperties.getId(), token)
          .compose(entitlements -> {
            var tenantIdToAppId = buildTenantIdToAppIdMap(entitlements);
            var tenantIds = List.copyOf(tenantIdToAppId.keySet());
            return tenantManagerClient.getTenantInfo(tenantIds, token)
              .onSuccess(tenants -> addEnabledTenants(tenants, tenantIdToAppId))
              .onFailure(throwable -> log.warn("Failed to load tenants", throwable));
          })
          .onFailure(throwable -> log.warn("Failed to load tenant entitlements", throwable)))
    );

    retryFuture.onComplete(unused -> promise.complete(), promise::fail);
  }

  private void addEnabledTenants(List<Tenant> tenants, Map<String, String> tenantIdToAppId) {
    tenantApplications.clear();
    for (var tenant : tenants) {
      var appId = tenantIdToAppId.get(tenant.getId().toString());
      if (appId != null) {
        tenantApplications.computeIfAbsent(tenant.getName(), k -> ConcurrentHashMap.newKeySet()).add(appId);
      }
    }
    log.info("Module is enabled for tenants: {}", () -> tenants.stream().map(Tenant::getName).toList());
    notifyEntitlementsChanged();
  }

  private void notifyEntitlementsChanged() {
    eventBus.publish(EntitlementsEvent.ENTITLEMENTS_EVENT,
      EntitlementsEvent.of(Set.copyOf(tenantApplications.keySet())));
  }

  private static Map<String, String> buildTenantIdToAppIdMap(ResultList<Entitlement> resultList) {
    return resultList.getRecords().stream()
      .collect(Collectors.toMap(Entitlement::getTenantId, Entitlement::getApplicationId, (a, b) -> a));
  }
}
