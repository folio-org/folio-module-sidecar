package org.folio.sidecar.service.routing;

import static java.util.stream.Collectors.joining;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.PRIMARY;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.REQUIRED;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.PermissionsUtils.findAllModulePermissions;

import io.quarkus.arc.All;
import io.quarkus.runtime.Quarkus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.kafka.DiscoveryListener;
import org.folio.sidecar.service.ModulePermissionsService;
import org.folio.sidecar.service.routing.configuration.RequestHandler;
import org.folio.sidecar.service.routing.lookup.TenantEgressRoutingService;

@Log4j2
@ApplicationScoped
public class RoutingService implements DiscoveryListener {

  private final ApplicationManagerService appManagerService;
  private final List<Handler<RoutingContext>> requestHandlers;
  private final List<ModuleBootstrapListener> moduleBootstrapListeners;
  private final Map<String, ModuleType> knownModules = new HashMap<>();
  private final ModulePermissionsService modulePermissionsService;
  private final TenantEgressRoutingService tenantEgressRoutingService;

  public RoutingService(ApplicationManagerService appManagerService,
    @RequestHandler @All List<Handler<RoutingContext>> requestHandlers, @All List<ModuleBootstrapListener> mbListeners,
    ModulePermissionsService modulePermissionsService, TenantEgressRoutingService tenantEgressRoutingService) {
    this.appManagerService = appManagerService;

    if (isEmpty(requestHandlers)) {
      throw new IllegalArgumentException("Request handlers are not configured");
    }
    this.requestHandlers = requestHandlers;

    this.moduleBootstrapListeners = mbListeners;
    this.modulePermissionsService = modulePermissionsService;
    this.tenantEgressRoutingService = tenantEgressRoutingService;
  }

  public Future<Void> init(Router router) {
    return appManagerService.getModuleBootstrapIngress()
      .map(moduleBootstrap -> {
        initFromBootstrap(router, moduleBootstrap);
        return moduleBootstrap;
      })
      .onFailure(error -> log.error("Failed to initialize ingress routes", error))
      .mapEmpty();
  }

  @Override
  public void onDiscovery(String moduleId) {
    updateModuleRoutes(moduleId);
    tenantEgressRoutingService.onDiscovery(moduleId);
  }

  public void updateModuleRoutes(String moduleId) {
    var type = knownModules.get(moduleId);
    if (type == null) {
      return;
    }

    if (type == REQUIRED) {
      // A required (provider) module's discovery only changes egress targets, which are refreshed per-tenant
      // by TenantEgressRoutingService.onDiscovery. The sidecar's own ingress routes are unaffected, so there is
      // no need to reload the ingress bootstrap here.
      log.debug("Discovery for required module [{}]: egress refreshed per-tenant, ingress reload skipped", moduleId);
      return;
    }

    refreshPrimaryModuleRoutes();
  }

  private Future<Void> refreshPrimaryModuleRoutes() {
    return appManagerService.getModuleBootstrapIngress()
      .map(moduleBootstrap -> {
        moduleBootstrapListeners.forEach(listener -> listener.onModuleBootstrap(moduleBootstrap.getModule(), UPDATE));
        modulePermissionsService.putPermissions(findAllModulePermissions(moduleBootstrap));
        return moduleBootstrap;
      })
      .onFailure(error -> {
        log.error("Failed to update routes", error);
        Quarkus.asyncExit(1);
      })
      .mapEmpty();
  }

  private void initFromBootstrap(Router router, ModuleBootstrap moduleBootstrap) {
    log.debug("Loaded module bootstrap: {}", moduleBootstrap);

    // Egress is now driven per-tenant by TenantEgressRoutingService (scoped tables + gateway fallback), so the old
    // global onRequiredModulesBootstrap notification is no longer consumed by anyone and is not fired.
    moduleBootstrapListeners.forEach(listener -> listener.onModuleBootstrap(moduleBootstrap.getModule(), INIT));

    modulePermissionsService.putPermissions(findAllModulePermissions(moduleBootstrap));

    var route = router.route("/*");
    requestHandlers.forEach(route::handler);

    registerKnownModules(moduleBootstrap);

    log.info("Sidecar initialized from module bootstrap: moduleId = {}, applicationId = {}",
      moduleBootstrap.getModule().getModuleId(), moduleBootstrap.getModule().getApplicationId());
  }

  private void registerKnownModules(ModuleBootstrap moduleBootstrap) {
    knownModules.clear();

    knownModules.put(moduleBootstrap.getModule().getModuleId(), PRIMARY);

    moduleBootstrap.getRequiredModules().forEach(
      discovery -> knownModules.put(discovery.getModuleId(), REQUIRED));

    log.info("Known modules registered: {}", this::getKnownModulesAsString);
  }

  private String getKnownModulesAsString() {
    return knownModules.entrySet().stream()
      .map(moduleEntry -> String.format("[%s, %s]", moduleEntry.getKey(), moduleEntry.getValue()))
      .collect(joining(", "));
  }

  enum ModuleType {
    PRIMARY, REQUIRED
  }
}
