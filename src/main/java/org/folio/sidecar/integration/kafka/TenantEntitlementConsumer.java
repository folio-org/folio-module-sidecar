package org.folio.sidecar.integration.kafka;

import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.ENTITLE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.UPGRADE;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.lookup.TenantEgressRoutingService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TenantEntitlementConsumer {

  private final TenantService tenantService;
  private final TenantEgressRoutingService tenantEgressRoutingService;

  @Incoming("entitlement")
  public CompletionStage<Void> consume(TenantEntitlementEvent event) {
    log.debug("Consuming entitlement event: {}", event);
    var moduleId = event.getModuleId();
    if (!tenantService.isAssignedModule(moduleId)) {
      return CompletableFuture.completedFuture(null);
    }

    var tenantName = event.getTenantName();
    if (shouldEnableTenant(event.getType())) {
      tenantService.enableTenant(tenantName);
    } else {
      tenantService.disableTenant(tenantName);
    }

    // Trigger the egress refresh but acknowledge the event immediately. refreshTenant serializes per tenant (so
    // ordering is preserved) and handles/logs its own failures. Acking now prevents a slow or failing refresh from
    // (a) blocking the partition head-of-line for the full retry window, or (b) nacking the message - which, under
    // the connector's default fail strategy, would halt consumption of all subsequent entitlement events.
    tenantEgressRoutingService.refreshTenant(tenantName);
    return CompletableFuture.completedFuture(null);
  }

  private boolean shouldEnableTenant(Type type) {
    return type == ENTITLE || type == UPGRADE;
  }
}
