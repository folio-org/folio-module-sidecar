package org.folio.sidecar.integration.kafka;

import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.ENTITLE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.UPGRADE;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.EgressBootstrapService;
import org.folio.sidecar.service.routing.lookup.TenantModuleResolver;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TenantEntitlementConsumer {

  @ConfigProperty(name = "routing.tenant-scoped.enabled") boolean tenantScoped;

  private final TenantService tenantService;
  private final EgressBootstrapService egressBootstrapService;
  private final TenantModuleResolver tenantModuleResolver;

  @Incoming("entitlement")
  public void consume(TenantEntitlementEvent event) {
    log.debug("Consuming entitlement event: {}", event);
    notifyModuleResolver(event);

    var moduleId = event.getModuleId();
    if (!tenantService.isAssignedModule(moduleId)) {
      return;
    }

    var tenantName = event.getTenantName();
    var type = event.getType();
    if (shouldEnableTenant(type)) {
      tenantService.enableTenant(tenantName);
      if (tenantScoped && type == UPGRADE) {
        egressBootstrapService.refreshTenant(tenantName);
      }
      return;
    }

    tenantService.disableTenant(tenantName);
  }

  /**
   * Notifies the module resolver before the assigned-module guard below, because the events it needs are about
   * another module. Isolated so a defect here cannot nack the message and stop the channel.
   *
   * @param event - tenant entitlement event
   */
  private void notifyModuleResolver(TenantEntitlementEvent event) {
    try {
      tenantModuleResolver.onEntitlementEvent(event);
    } catch (Exception error) {
      log.warn("Failed to update tenant module binding from entitlement event: {}", event, error);
    }
  }

  private boolean shouldEnableTenant(Type type) {
    return type == ENTITLE || type == UPGRADE;
  }
}
