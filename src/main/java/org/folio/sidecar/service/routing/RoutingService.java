package org.folio.sidecar.service.routing;

import static java.util.stream.Collectors.joining;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.INIT;
import static org.folio.sidecar.service.routing.ModuleBootstrapListener.ChangeType.UPDATE;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.PRIMARY;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.REQUIRED;

import io.quarkus.runtime.Quarkus;
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
import org.folio.sidecar.service.routing.configuration.RequestHandler;

@Log4j2
@ApplicationScoped
public class RoutingService {

  private final ApplicationManagerService appManagerService;
  private final Handler<RoutingContext> requestHandler;
  private final List<ModuleBootstrapListener> moduleBootstrapListeners;
  private final Map<String, ModuleType> knownModules = new HashMap<>();

  public RoutingService(ApplicationManagerService appManagerService,
    @RequestHandler Handler<RoutingContext> requestHandler, Instance<ModuleBootstrapListener> mbListeners) {
    this.appManagerService = appManagerService;
    this.requestHandler = requestHandler;
    this.moduleBootstrapListeners = mbListeners.stream().toList();
  }

  public void initRoutes(Router router) {
    loadBootstrapAndProcess(moduleBootstrap -> initFromBootstrap(router, moduleBootstrap));
  }

  public void updateModuleRoutes(String moduleId) {
    var type = knownModules.get(moduleId);

    if (type != null) {
      loadBootstrapAndProcess(updateModuleRoutesByType(type));
    }
  }

  private void loadBootstrapAndProcess(Consumer<ModuleBootstrap> consumer) {
    appManagerService.getModuleBootstrap()
      .onSuccess(consumer::accept)
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

    router.route("/*").handler(requestHandler);

    registerKnownModules(moduleBootstrap);
  }

  private void registerKnownModules(ModuleBootstrap moduleBootstrap) {
    knownModules.clear();

    knownModules.put(moduleBootstrap.getModule().getModuleId(), PRIMARY);

    moduleBootstrap.getRequiredModules().forEach(
      discovery -> knownModules.put(discovery.getModuleId(), REQUIRED));

    log.info("Known modules registered: {}", this::getKnownModulesAsString);
  }

  private Consumer<ModuleBootstrap> updateModuleRoutesByType(ModuleType type) {
    return moduleBootstrap -> {
      if (type == PRIMARY) {
        moduleBootstrapListeners.forEach(listener -> listener.onModuleBootstrap(moduleBootstrap.getModule(), UPDATE));
        return;
      }

      moduleBootstrapListeners.forEach(listener -> listener
        .onRequiredModulesBootstrap(moduleBootstrap.getRequiredModules(), UPDATE));
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
