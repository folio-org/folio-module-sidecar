package org.folio.sidecar.service.routing;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.PRIMARY;
import static org.folio.sidecar.service.routing.RoutingService.ModuleType.REQUIRED;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.PermissionsUtils.extractPermissions;
import static org.folio.sidecar.utils.RoutingUtils.dumpHeaders;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.quarkus.runtime.Quarkus;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;
import org.folio.sidecar.configuration.properties.RoutingHandlerProperties;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.ModulePermissionsService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class RoutingService {

  private final ApplicationManagerService appManagerService;
  private final ErrorHandler errorHandler;
  private final EgressRequestHandler egressRequestHandler;
  private final IngressRequestHandler ingressRequestHandler;
  private final RequestMatchingService requestMatchingService;
  private final ModulePermissionsService modulePermissionsService;
  private final RoutingHandlerProperties routingHandlerProperties;

  private final Map<String, ModuleType> knownModules = new HashMap<>();

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

    requestMatchingService.bootstrapModule(moduleBootstrap);

    modulePermissionsService.putPermissions(extractPermissions(moduleBootstrap));

    var routerRequestHandler = createRequestHandler();

    router.route("/*").handler(routerRequestHandler);

    registerKnownModules(moduleBootstrap);
  }

  private Handler<RoutingContext> createRequestHandler() {
    var requestHandler = new ScRequestHandler(ingressRequestHandler, egressRequestHandler,
      requestMatchingService, errorHandler);

    if (!routingHandlerProperties.tracing().enabled()) {
      return requestHandler;
    } else {
      var paths = routingHandlerProperties.tracing().paths().orElseGet(Collections::emptyList);
      log.info("Header tracing is activated: paths = {}", isEmpty(paths) ? "<all>" : paths);
      return new TraceHeadersHandler(requestHandler, paths, log);
    }
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
        requestMatchingService.updateIngressRoutes(moduleBootstrap.getModule());
        modulePermissionsService.putPermissions(extractPermissions(moduleBootstrap));
        return;
      }

      requestMatchingService.updateEgressRoutes(moduleBootstrap.getRequiredModules());
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

  @RequiredArgsConstructor
  private static final class TraceHeadersHandler implements Handler<RoutingContext> {

    private final Handler<RoutingContext> decorated;
    private final Collection<String> paths;
    private final Logger log;

    @Override
    public void handle(RoutingContext rc) {
      var req = rc.request();
      if (pathMatched(req.path())) {
        log.debug("""
        \n======================================
        Request: method = {}, uri = {}
        Current state of request context:
        ********** Headers *******************
        {}""", req::method, dumpUri(rc), dumpHeaders(rc));
      }
      decorated.handle(rc);
    }

    private boolean pathMatched(@Nullable String path) {
      return isEmpty(paths) || paths.stream().anyMatch(s -> containsIgnoreCase(path, s));
    }
  }
}
