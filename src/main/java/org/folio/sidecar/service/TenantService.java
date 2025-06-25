package org.folio.sidecar.service;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import io.quarkus.scheduler.Scheduled;
import io.vertx.core.Future;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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

  private final ServiceTokenProvider tokenProvider;
  private final RetryTemplate retryTemplate;
  private final TenantManagerClient tenantManagerClient;
  private final TenantEntitlementClient tenantEntitlementClient;
  private final ModuleProperties moduleProperties;
  private final EventBus eventBus;
  private final Set<String> enabledTenants = new ConcurrentHashSet<>();
  private final AtomicBoolean canExecuteTenantsAndEntitlementsTask = new AtomicBoolean(false);
  private final AtomicReference<Future<List<Tenant>>> tenantsAndEntitlementsTask = new AtomicReference<>(null);

  public Future<Void> init() {
    Future<List<Tenant>> lte = loadTenantsAndEntitlements();
    tenantsAndEntitlementsTask.set(lte);
    
    return lte.map((Void) null).onSuccess(unused ->
      log.info("Successfully initialized tenant entitlements for module: {}", moduleProperties.getId()));
  }

  public void enableTenant(String tenantName) {
    if (enabledTenants.add(tenantName)) {
      log.info("Enabling tenant: {}", tenantName);
      notifyEntitlementsChanged();
    }
  }

  public void disableTenant(String tenantName) {
    if (enabledTenants.remove(tenantName)) {
      log.info("Disabling tenant: {}", tenantName);
      notifyEntitlementsChanged();
    }
  }

  public Future<List<Tenant>> loadTenantsAndEntitlements() {
    return retryTemplate.callAsync(() ->
      tokenProvider.getAdminToken().compose(token ->
        tenantEntitlementClient.getModuleEntitlements(moduleProperties.getId(), token)
          .map(TenantService::getTenantIds)
          .compose(tenantIds -> tenantManagerClient.getTenantInfo(tenantIds, token)
            .onSuccess(this::addEnabledTenants)
            .onFailure(throwable -> log.warn("Failed to load tenants", throwable)))
          .onFailure(throwable -> log.warn("Failed to load tenant entitlements", throwable)))
    );
  }

  public boolean isAssignedModule(String moduleId) {
    return Objects.equals(moduleProperties.getId(), moduleId);
  }

  public boolean isEnabledTenant(String name) {
    if (isEmpty(name)) {
      return false;
    }
    return enabledTenants.contains(name);
  }

  /**
    * This logic is implemented in scope of MODSIDECAR-126.
    * Should be removed after design long term solution.
  */
  public void executeTenantsAndEntitlementsTask() {
    var currentTask = tenantsAndEntitlementsTask.get();
    if (canExecuteTenantsAndEntitlementsTask.get() && currentTask != null && currentTask.isComplete()) {
      canExecuteTenantsAndEntitlementsTask.set(false);
      tenantsAndEntitlementsTask.set(loadTenantsAndEntitlements());
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

  private static List<String> getTenantIds(ResultList<Entitlement> resultList) {
    return resultList.getRecords()
      .stream().map(Entitlement::getTenantId)
      .toList();
  }

  private void addEnabledTenants(List<Tenant> tenants) {
    enabledTenants.clear();
    tenants.forEach(tenant -> enabledTenants.add(tenant.getName()));
    log.info("Module is enabled for tenants: {}", tenants);
    notifyEntitlementsChanged();
  }

  private void notifyEntitlementsChanged() {
    eventBus.publish(EntitlementsEvent.ENTITLEMENTS_EVENT, EntitlementsEvent.of(enabledTenants));
  }
}
