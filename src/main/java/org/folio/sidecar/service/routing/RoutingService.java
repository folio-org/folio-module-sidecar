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
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.exception.RoutesNotInitializedException;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.kafka.DiscoveryListener;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.ModulePermissionsService;
import org.folio.sidecar.service.routing.configuration.RequestHandler;

@Log4j2
@ApplicationScoped
public class RoutingService implements DiscoveryListener {

  @ConfigProperty(name = "routing.tenant-scoped.enabled") boolean tenantScoped;

  private final ApplicationManagerService appManagerService;
  private final List<Handler<RoutingContext>> requestHandlers;
  private final List<ModuleBootstrapListener> moduleBootstrapListeners;
  private final Map<String, ModuleType> knownModules = new HashMap<>();
  private final ModulePermissionsService modulePermissionsService;
  private final EgressBootstrapService egressBootstrapService;
  private final ModuleProperties moduleProperties;
  private final ErrorHandler errorHandler;
  private volatile boolean initialized;

  public RoutingService(ApplicationManagerService appManagerService,
    @RequestHandler @All List<Handler<RoutingContext>> requestHandlers, @All List<ModuleBootstrapListener> mbListeners,
    ModulePermissionsService modulePermissionsService, EgressBootstrapService egressBootstrapService,
    ModuleProperties moduleProperties, ErrorHandler errorHandler) {
    this.appManagerService = appManagerService;

    if (isEmpty(requestHandlers)) {
      throw new IllegalArgumentException("Request handlers are not configured");
    }
    this.requestHandlers = requestHandlers;

    this.moduleBootstrapListeners = mbListeners;
    this.modulePermissionsService = modulePermissionsService;
    this.egressBootstrapService = egressBootstrapService;
    this.moduleProperties = moduleProperties;
    this.errorHandler = errorHandler;
  }

  public Future<Void> init(Router router) {
    var route = router.route("/*");
    route.handler(this::handleIfInitialized);
    requestHandlers.forEach(route::handler);

    var bootstrap = tenantScoped ? appManagerService.getIngressBootstrap() : appManagerService.getModuleBootstrap();
    return process(bootstrap, this::initFromBootstrap);
  }

  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void onDiscovery(String moduleId) {
    if (tenantScoped) {
      if (!moduleProperties.getId().equals(moduleId)) {
        egressBootstrapService.refreshAllTenants();
      }
      return;
    }
    updateModuleRoutes(moduleId);
  }

  public void updateModuleRoutes(String moduleId) {
    var type = knownModules.get(moduleId);

    if (type != null) {
      process(appManagerService.getModuleBootstrap(), updateModuleRoutesByType(type, moduleId));
    }
  }

  private Future<Void> process(Future<ModuleBootstrap> bootstrap, Consumer<ModuleBootstrap> consumer) {
    return bootstrap
      .map(moduleBootstrap -> {
        consumer.accept(moduleBootstrap);
        return (Void) null;
      })
      .onFailure(error -> {
        log.error("Failed to initialize routes", error);
        Quarkus.asyncExit(1);
      });
  }

  private void handleIfInitialized(RoutingContext rc) {
    if (initialized) {
      rc.next();
      return;
    }
    errorHandler.sendErrorResponse(rc, new RoutesNotInitializedException());
  }

  private void initFromBootstrap(ModuleBootstrap moduleBootstrap) {
    log.debug("Loaded module bootstrap: {}", moduleBootstrap);

    moduleBootstrapListeners.forEach(listener -> {
      listener.onModuleBootstrap(moduleBootstrap.getModule(), INIT);
      listener.onRequiredModulesBootstrap(moduleBootstrap.getRequiredModules(), INIT);
    });

    modulePermissionsService.putPermissions(findAllModulePermissions(moduleBootstrap));
    registerKnownModules(moduleBootstrap);
    initialized = true;

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
