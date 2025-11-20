package org.folio.sidecar.service.routing.configuration;

import static org.folio.sidecar.utils.CollectionUtils.isEmpty;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.arc.lookup.LookupUnlessProperty;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.ResponseTimeHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Named;
import java.util.Collections;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.routing.configuration.properties.DynamicRoutingProperties;
import org.folio.sidecar.service.routing.configuration.properties.TraceRoutingProperties;
import org.folio.sidecar.service.routing.handler.ChainedHandler;
import org.folio.sidecar.service.routing.handler.RoutingEntryHandler;
import org.folio.sidecar.service.routing.handler.RoutingHandlerWithLookup;
import org.folio.sidecar.service.routing.handler.ScRequestHandler;
import org.folio.sidecar.service.routing.handler.TraceHeadersHandler;
import org.folio.sidecar.service.routing.lookup.DiscoveryCacheFactory;
import org.folio.sidecar.service.routing.lookup.DiscoveryCacheUpdator;
import org.folio.sidecar.service.routing.lookup.DynamicRoutingLookup;
import org.folio.sidecar.service.routing.lookup.GatewayRoutingLookup;
import org.folio.sidecar.service.routing.lookup.RoutingLookup;

@Log4j2
public class RoutingConfiguration {

  @Named
  @ApplicationScoped
  public ChainedHandler basicIngressHandler(@Named("ingressLookup") RoutingLookup lookup,
    @Named("ingressRequestHandler") RoutingEntryHandler handler, PathProcessor pathProcessor) {
    return new RoutingHandlerWithLookup(lookup, handler, pathProcessor);
  }

  @Named
  @ApplicationScoped
  @LookupUnlessProperty(name = "routing.forward-to-gateway.always", stringValue = "true")
  public ChainedHandler basicEgressHandler(@Named("egressLookup") RoutingLookup lookup,
    @Named("egressRequestHandler") RoutingEntryHandler handler, PathProcessor pathProcessor) {
    return new RoutingHandlerWithLookup(lookup, handler, pathProcessor);
  }

  @Named
  @ApplicationScoped
  public ChainedHandler chainedHandler(@Named("basicIngressHandler") ChainedHandler ingressHandler,
    @Named("basicEgressHandler") Instance<ChainedHandler> egressHandler,
    @Named("dynamicEgressHandler") Instance<ChainedHandler> dynamicEgressHandler,
    @Named("gatewayEgressHandler") Instance<ChainedHandler> gatewayEgressHandler,
    @Named("notFoundHandler") ChainedHandler notFoundHandler) {
    var handler = ingressHandler;

    if (egressHandler.isResolvable()) {
      handler = handler.next(egressHandler.get());
      log.debug("Egress handler added to the handlers chain");
    }

    if (dynamicEgressHandler.isResolvable()) {
      handler = handler.next(dynamicEgressHandler.get());
      log.debug("Dynamic egress handler added to the handlers chain");
    }

    if (gatewayEgressHandler.isResolvable()) {
      handler = handler.next(gatewayEgressHandler.get());
      log.debug("Gateway egress handler added to the handlers chain");
    }

    return handler.next(notFoundHandler);
  }

  @RequestHandler
  @ApplicationScoped
  @LookupUnlessProperty(name = "routing.tracing.enabled", stringValue = "true")
  public Handler<RoutingContext> requestHandler(@Named("chainedHandler") ChainedHandler chainedHandler,
    ErrorHandler errorHandler) {
    return new ScRequestHandler(chainedHandler, errorHandler);
  }

  @RequestHandler
  @ApplicationScoped
  @LookupIfProperty(name = "routing.tracing.enabled", stringValue = "true")
  public Handler<RoutingContext> requestHandlerWithTracing(@Named("chainedHandler") ChainedHandler chainedHandler,
    ErrorHandler errorHandler, TraceRoutingProperties traceRoutingProperties) {
    var paths = traceRoutingProperties.paths().orElseGet(Collections::emptyList);
    log.info("Header tracing is activated: paths = {}", isEmpty(paths) ? "<all>" : paths);

    return new TraceHeadersHandler(new ScRequestHandler(chainedHandler, errorHandler), paths);
  }

  @RequestHandler
  @Priority(10)
  @ApplicationScoped
  @LookupIfProperty(name = "routing.logger.enabled", stringValue = "true")
  public Handler<RoutingContext> loggerHandler() {
    return LoggerHandler.create(true, LoggerFormat.DEFAULT);
  }

  @RequestHandler
  @Priority(9)
  @ApplicationScoped
  @LookupIfProperty(name = "routing.response-time.enabled", stringValue = "true")
  public Handler<RoutingContext> responseTimeHandler() {
    return ResponseTimeHandler.create();
  }

  public static class Dynamic {

    @ApplicationScoped
    public DiscoveryCacheFactory discoveryCacheFactory(ApplicationManagerService applicationManagerService) {
      return new DiscoveryCacheFactory(applicationManagerService);
    }

    @Named("dynamicRoutingDiscoveryCache")
    @ApplicationScoped
    public AsyncLoadingCache<String, ModuleDiscovery> discoveryCache(DiscoveryCacheFactory factory,
      DynamicRoutingProperties properties) {
      return factory.createCache(properties.discoveryCache());
    }

    @ApplicationScoped
    @LookupIfProperty(name = "routing.dynamic.enabled", stringValue = "true")
    public DiscoveryCacheUpdator discoveryCacheUpdator(
      @Named("dynamicRoutingDiscoveryCache") AsyncLoadingCache<String, ModuleDiscovery> discoveryCache) {
      return new DiscoveryCacheUpdator(discoveryCache);
    }

    @Named("dynamicLookup")
    @ApplicationScoped
    public RoutingLookup dynamicRoutingLookup(TenantEntitlementService tenantEntitlementService,
      @Named("dynamicRoutingDiscoveryCache") AsyncLoadingCache<String, ModuleDiscovery> discoveryCache) {
      return new DynamicRoutingLookup(tenantEntitlementService, discoveryCache);
    }

    @Named
    @ApplicationScoped
    @LookupIfProperty(name = "routing.dynamic.enabled", stringValue = "true")
    public ChainedHandler dynamicEgressHandler(@Named("dynamicLookup") RoutingLookup lookup,
      @Named("egressRequestHandler") RoutingEntryHandler handler, PathProcessor pathProcessor) {
      return new RoutingHandlerWithLookup(lookup, handler, pathProcessor);
    }
  }

  public static class Gateway {

    @Named("gatewayLookup")
    @ApplicationScoped
    public RoutingLookup gatewayRoutingLookup(
      @ConfigProperty(name = "routing.forward-to-gateway.destination") String gatewayDestination,
      SidecarProperties sidecarProperties) {
      return new GatewayRoutingLookup(gatewayDestination, sidecarProperties);
    }

    @Named
    @ApplicationScoped
    @LookupIfProperty(name = "routing.forward-to-gateway.enabled", stringValue = "true")
    public ChainedHandler gatewayEgressHandler(@Named("gatewayLookup") RoutingLookup lookup,
      @Named("egressRequestHandler") RoutingEntryHandler handler, PathProcessor pathProcessor) {
      return new RoutingHandlerWithLookup(lookup, handler, pathProcessor);
    }
  }
}
