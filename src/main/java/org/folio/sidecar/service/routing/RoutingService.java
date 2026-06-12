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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.kafka.DiscoveryListener;
import org.folio.sidecar.service.ModulePermissionsService;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.configuration.RequestHandler;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.utils.GenericCompositeFuture;

@Log4j2
@ApplicationScoped
public class RoutingService implements DiscoveryListener {

  private final ApplicationManagerService appManagerService;
  private final List<Handler<RoutingContext>> requestHandlers;
  private final List<ModuleBootstrapListener> moduleBootstrapListeners;
  private final Map<String, ModuleType> knownModules = new HashMap<>();
  private final ModulePermissionsService modulePermissionsService;
  private final TenantService tenantService;
  private final EgressRoutingLookup egressLookup;

  public RoutingService(ApplicationManagerService appManagerService,
    @RequestHandler @All List<Handler<RoutingContext>> requestHandlers, @All List<ModuleBootstrapListener> mbListeners,
    ModulePermissionsService modulePermissionsService, TenantService tenantService, EgressRoutingLookup egressLookup) {
    this.appManagerService = appManagerService;

    if (isEmpty(requestHandlers)) {
      throw new IllegalArgumentException("Request handlers are not configured");
    }
    this.requestHandlers = requestHandlers;

    this.moduleBootstrapListeners = mbListeners;
    this.modulePermissionsService = modulePermissionsService;
    this.tenantService = tenantService;
    this.egressLookup = egressLookup;
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

  /**
   * Loads per-application egress bootstrap for every applicationId currently known to {@link TenantService}.
   *
   * <p>Must be called after {@link TenantService#init()} has populated tenant→application mappings;
   * otherwise the iteration sees an empty set and no per-application caches are built.
   *
   * <p>Per-application failures are logged and swallowed so a single failing application does not block
   * the others; the returned future always succeeds.
   */
  public Future<Void> loadEgressBootstrapPerApplication() {
    var applicationIds = tenantService.getAllApplicationIds();
    if (applicationIds.isEmpty()) {
      return Future.succeededFuture();
    }
    var futures = new ArrayList<Future<Void>>(applicationIds.size());
    for (var applicationId : applicationIds) {
      Future<Void> f = appManagerService.getModuleBootstrap(applicationId)
        .onSuccess(bootstrap -> egressLookup.onApplicationBootstrap(applicationId, bootstrap.getRequiredModules()))
        .onFailure(error -> log.warn("Failed to load egress bootstrap for application: {}", applicationId, error))
        .recover(error -> Future.succeededFuture(null))
        .mapEmpty();
      futures.add(f);
    }
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  private Future<Void> loadBootstrapAndProcess(Consumer<ModuleBootstrap> consumer) {
    return appManagerService.getModuleBootstrap()
      .map(moduleBootstrap -> {
        consumer.accept(moduleBootstrap);
        return null;
      })
      .onFailure(error -> {
        log.error("Failed to initialize routes", error);
        Quarkus.asyncExit(0);
      })
      .mapEmpty();
  }

  private void initFromBootstrap(Router router, ModuleBootstrap moduleBootstrap) {
    log.debug("Loaded module bootstrap: {}", moduleBootstrap);

    moduleBootstrapListeners.forEach(listener -> {
      listener.onModuleBootstrap(moduleBootstrap.getModule(), INIT);
      listener.onRequiredModulesBootstrap(moduleBootstrap.getRequiredModules(), INIT);
    });

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
