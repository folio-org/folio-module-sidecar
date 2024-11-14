package org.folio.sidecar.service;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.te.TenantEntitlementClient;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.integration.tm.TenantManagerClient;
import org.folio.sidecar.integration.tm.model.Tenant;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;

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
  @Getter
  private final Set<String> enabledTenants = new ConcurrentHashSet<>();

  public void init(@Observes StartupEvent event) {
    loadTenantsAndEntitlements();
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

  public void loadTenantsAndEntitlements() {
    retryTemplate.callAsync(() ->
      tokenProvider.getAdminToken().compose(token ->
        tenantEntitlementClient.getEntitlements(moduleProperties.getId(), token)
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
    return enabledTenants.contains(name);
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
