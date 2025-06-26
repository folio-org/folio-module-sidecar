package org.folio.sidecar.service.routing;

import static java.util.stream.Collectors.joining;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.PRIMARY;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.REQUIRED;
import static org.folio.sidecar.utils.PermissionsUtils.findAllModulePermissions;

import io.quarkus.arc.All;
import io.quarkus.runtime.Quarkus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.kafka.DiscoveryListener;
import org.folio.sidecar.service.ModulePermissionsService;
import org.folio.sidecar.service.routing.configuration.RequestHandler;

@Log4j2
@ApplicationScoped
public class RoutingService implements DiscoveryListener {

  private final ApplicationManagerService appManagerService;
  private final Handler<RoutingContext> requestHandler;
  private final List<ModuleBootstrapListener> moduleBootstrapListeners;
  private final Map<String, ModuleType> knownModules = new HashMap<>();
  private final ModulePermissionsService modulePermissionsService;

  public RoutingService(ApplicationManagerService appManagerService,
    @RequestHandler Instance<Handler<RoutingContext>> requestHandler, @All List<ModuleBootstrapListener> mbListeners,
    ModulePermissionsService modulePermissionsService) {
    this.appManagerService = appManagerService;
    this.requestHandler = requestHandler.get();
    this.moduleBootstrapListeners = mbListeners;
    this.modulePermissionsService = modulePermissionsService;
  }

  public Future<Void> init(Router router) {
    return loadBootstrapAndProcess(moduleBootstrap -> initFromBootstrap(router, moduleBootstrap));
  }

  @Override
  public void onDiscovery(String moduleId) {
    updateModuleRoutes(moduleId);
  }

  public void updateModuleRoutes(String moduleId) {
    var type = knownModules.get(moduleId);

    if (type != null) {
      loadBootstrapAndProcess(updateModuleRoutesByType(type, moduleId));
    }
  }

  private Future<Void> loadBootstrapAndProcess(Consumer<ModuleBootstrap> consumer) {
    return appManagerService.getModuleBootstrap()
      .map(moduleBootstrap -> {
        consumer.accept(moduleBootstrap);
        return (Void) null;
      })
      .onFailure(error -> {
        log.error("Failed to initialize routes", error);
        Quarkus.asyncExit(0);
      });
  }

  private void initFromBootstrap(Router router, ModuleBootstrap moduleBootstrap) {
    log.debug("Loaded module bootstrap: {}", moduleBootstrap);

    moduleBootstrapListeners.forEach(listener -> {
      listener.onModuleBootstrap(moduleBootstrap.getModule(), INIT);
      listener.onRequiredModulesBootstrap(moduleBootstrap.getRequiredModules(), INIT);
    });

    modulePermissionsService.putPermissions(findAllModulePermissions(moduleBootstrap));

    router.route("/*").handler(requestHandler);

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

  private Consumer<ModuleBootstrap> updateModuleRoutesByType(ModuleType type, String updatedModuleId) {
    return moduleBootstrap -> {
      if (type == PRIMARY) {
        moduleBootstrapListeners.forEach(listener -> listener.onModuleBootstrap(moduleBootstrap.getModule(), UPDATE));
        modulePermissionsService.putPermissions(findAllModulePermissions(moduleBootstrap));
        return;
      }

      moduleBootstrapListeners.forEach(listener -> listener
        .onRequiredModulesBootstrap(moduleBootstrap.getRequiredModules(), UPDATE));

      log.info("Sidecar updated from module bootstrap: moduleId = {}, moduleType = {}, applicationId = {}",
        updatedModuleId, type, moduleBootstrap.getModule().getApplicationId());
    };
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
