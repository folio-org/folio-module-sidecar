package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.succeededFuture;

import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Future;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class EgressBootstrapService {

  @ConfigProperty(name = "routing.tenant-scoped.enabled") boolean tenantScoped;

  private final ApplicationManagerService appManagerService;
  private final TenantEntitlementService tenantEntitlementService;
  private final EgressRoutingLookup egressRoutingLookup;
  private final Set<String> tenants = new ConcurrentHashSet<>();

  @ConsumeEvent(value = EntitlementsEvent.ENTITLEMENTS_EVENT, blocking = true)
  public void onEntitlementsChanged(EntitlementsEvent event) {
    if (!tenantScoped) {
      return;
    }
    var target = event.getTenants() == null ? Set.<String>of() : event.getTenants();
    target.stream().filter(tenant -> !tenants.contains(tenant)).forEach(this::buildEgress);
    Set.copyOf(tenants).stream().filter(tenant -> !target.contains(tenant)).forEach(this::dropTenant);
  }

  public void refreshTenant(String tenant) {
    if (tenantScoped) {
      buildEgress(tenant);
    }
  }

  public void refreshAllTenants() {
    if (tenantScoped) {
      Set.copyOf(tenants).forEach(this::buildEgress);
    }
  }

  private Future<Void> buildEgress(String tenant) {
    return tenantEntitlementService.getTenantEntitlements(tenant, false)
      .map(this::toApplicationIds)
      .compose(this::fetchBootstrap)
      .onSuccess(bootstrap -> {
        egressRoutingLookup.updateTenantEgressRoutes(tenant, bootstrap.getRequiredModules());
        tenants.add(tenant);
      })
      .onFailure(error -> log.warn("Failed to build egress for tenant {}: {}", tenant, error.getMessage()))
      .mapEmpty();
  }

  private Future<ModuleBootstrap> fetchBootstrap(List<String> appIds) {
    return appIds.isEmpty() ? succeededFuture(new ModuleBootstrap()) : appManagerService.getEgressBootstrap(appIds);
  }

  private void dropTenant(String tenant) {
    egressRoutingLookup.removeTenantEgressRoutes(tenant);
    tenants.remove(tenant);
  }

  private List<String> toApplicationIds(ResultList<Entitlement> entitlements) {
    return entitlements.getRecords().stream().map(Entitlement::getApplicationId).distinct().toList();
  }
}
