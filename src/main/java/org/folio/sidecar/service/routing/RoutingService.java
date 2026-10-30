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
import lombok.Getter;
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

/**
 * Loads the module's routes from mgr-applications at startup and keeps them up to date on discovery events.
 *
 * <p>With tenant-scoped routing disabled, one bootstrap provides both the ingress and the egress routes, and a
 * discovery event of a known module re-fetches it. With tenant-scoped routing enabled, startup loads only the
 * ingress routes; the egress routes are built per tenant by {@link EgressBootstrapService}, and a discovery event
 * of another module is passed to it.</p>
 *
 * <p>Requests are rejected with {@link RoutesNotInitializedException} until the routes are loaded.</p>
 */
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
  /**
   * -- GETTER --
   *  Tells whether the routes are loaded and requests are accepted.
   */
  @Getter
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

  /**
   * Registers the request handlers and loads the routes. The ingress bootstrap is used in tenant-scoped mode, the
   * full module bootstrap otherwise. If loading fails after the retries, the sidecar exits with code 1.
   *
   * @param router - the router to register the request handlers on
   * @return future completed when the routes are loaded
   */
  public Future<Void> init(Router router) {
    var route = router.route("/*");
    route.handler(this::handleIfInitialized);
    requestHandlers.forEach(route::handler);

    var bootstrap = tenantScoped ? appManagerService.getIngressBootstrap() : appManagerService.getModuleBootstrap();
    return process(bootstrap, this::initFromBootstrap);
  }

  /**
   * Applies a discovery change of a module. In tenant-scoped mode, a change of another module goes to
   * {@link EgressBootstrapService#onDiscovery(String)}, and a change of the sidecar's own module is ignored.
   * Otherwise the routes are re-fetched if the module is known (see {@link #updateModuleRoutes(String)}).
   *
   * @param moduleId - id of the module whose discovery changed
   */
  @Override
  public void onDiscovery(String moduleId) {
    if (tenantScoped) {
      if (!moduleProperties.getId().equals(moduleId)) {
        egressBootstrapService.onDiscovery(moduleId);
      }
      return;
    }
    updateModuleRoutes(moduleId);
  }

  /**
   * Re-fetches the module bootstrap and updates the routes of a known module: the sidecar's own routes and
   * permissions, or the egress routes of a required module. An unknown module is ignored.
   *
   * @param moduleId - id of the module whose discovery changed
   */
  public void updateModuleRoutes(String moduleId) {
    var type = knownModules.get(moduleId);

    if (type != null) {
      process(appManagerService.getModuleBootstrap(), updateModuleRoutesByType(type, moduleId));
    }
  }

  /**
   * Applies the loaded bootstrap. A failure is logged and stops the sidecar with exit code 1.
   *
   * @param bootstrap - the bootstrap being loaded
   * @param consumer - applies the loaded bootstrap
   */
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

  /**
   * Passes the request on once the routes are loaded, and answers with an error before that.
   */
  private void handleIfInitialized(RoutingContext rc) {
    if (initialized) {
      rc.next();
      return;
    }
    errorHandler.sendErrorResponse(rc, new RoutesNotInitializedException());
  }

  /**
   * Hands the startup bootstrap to the listeners, stores the module permissions, remembers the known modules and
   * marks the sidecar as initialized.
   */
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

  /**
   * Creates the action that applies a re-fetched bootstrap: the own module's routes and permissions for
   * {@link ModuleType#PRIMARY}, the required modules' egress routes for {@link ModuleType#REQUIRED}.
   *
   * @param type - type of the module whose discovery changed
   * @param updatedModuleId - id of that module
   */
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

  /**
   * Role of a module for this sidecar: its own module, or a module it calls.
   */
  enum ModuleType {
    PRIMARY, REQUIRED
  }
}
