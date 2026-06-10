package org.folio.sidecar.integration.kafka;

import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.ENTITLE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.UPGRADE;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TenantEntitlementConsumer {

  private final TenantService tenantService;
  private final ApplicationManagerService applicationManagerService;
  private final EgressRoutingLookup egressRoutingLookup;

  @Incoming("entitlement")
  public void consume(TenantEntitlementEvent event) {
    log.debug("Consuming entitlement event: {}", event);
    var moduleId = event.getModuleId();
    if (!tenantService.isAssignedModule(moduleId)) {
      return;
    }

    var tenantName = event.getTenantName();
    var applicationId = event.getApplicationId();
    if (shouldEnableTenant(event.getType())) {
      tenantService.enableTenant(tenantName, applicationId);
      applicationManagerService.getModuleBootstrap(applicationId)
        .onSuccess(bootstrap -> egressRoutingLookup.onApplicationBootstrap(applicationId,
          bootstrap.getRequiredModules()))
        .onFailure(err -> log.warn("Failed to load bootstrap for application {}: {}", applicationId, err.getMessage()));
      return;
    }

    tenantService.disableTenant(tenantName, applicationId);
    if (tenantService.getAllApplicationIds().stream().noneMatch(applicationId::equals)) {
      egressRoutingLookup.onApplicationRevoked(applicationId);
    }
  }

  private boolean shouldEnableTenant(Type type) {
    return type == ENTITLE || type == UPGRADE;
  }
}
