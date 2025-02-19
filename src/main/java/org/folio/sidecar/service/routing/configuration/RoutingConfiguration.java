package org.folio.sidecar.service.routing.configuration;

import static org.folio.sidecar.utils.CollectionUtils.isEmpty;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.arc.lookup.LookupUnlessProperty;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
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

  @Produces
  @Named
  @ApplicationScoped
  public ChainedHandler basicIngressHandler(@Named("ingressLookup") RoutingLookup lookup,
    @Named("ingressRequestHandler") RoutingEntryHandler handler,
    PathProcessor pathProcessor, ErrorHandler errorHandler) {
    return new RoutingHandlerWithLookup(lookup, handler, pathProcessor, errorHandler);
  }

  @Produces
  @Named
  @ApplicationScoped
  public ChainedHandler basicEgressHandler(@Named("egressLookup") RoutingLookup lookup,
    @Named("egressRequestHandler") RoutingEntryHandler handler,
    PathProcessor pathProcessor, ErrorHandler errorHandler) {
    return new RoutingHandlerWithLookup(lookup, handler, pathProcessor, errorHandler);
  }

  @Produces
  @Named
  @ApplicationScoped
  public ChainedHandler chainedHandler(@Named("basicIngressHandler") ChainedHandler ingressHandler,
    @Named("basicEgressHandler") ChainedHandler egressHandler,
    @Named("dynamicEgressHandler") Instance<ChainedHandler> dynamicEgressHandler,
    @Named("gatewayEgressHandler") Instance<ChainedHandler> gatewayEgressHandler,
    @Named("notFoundHandler") ChainedHandler notFoundHandler) {
    var handler = ingressHandler.next(egressHandler);

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

  @Produces
  @RequestHandler
  @ApplicationScoped
  @LookupUnlessProperty(name = "routing.tracing.enabled", stringValue = "true")
  public Handler<RoutingContext> requestHandler(@Named("chainedHandler") ChainedHandler chainedHandler,
    ErrorHandler errorHandler) {
    return new ScRequestHandler(chainedHandler, errorHandler);
  }

  @Produces
  @RequestHandler
  @ApplicationScoped
  @LookupIfProperty(name = "routing.tracing.enabled", stringValue = "true")
  public Handler<RoutingContext> requestHandlerWithTracing(@Named("chainedHandler") ChainedHandler chainedHandler,
    ErrorHandler errorHandler, TraceRoutingProperties traceRoutingProperties) {
    var paths = traceRoutingProperties.paths().orElseGet(Collections::emptyList);
    log.info("Header tracing is activated: paths = {}", isEmpty(paths) ? "<all>" : paths);

    return new TraceHeadersHandler(new ScRequestHandler(chainedHandler, errorHandler), paths);
  }

  @LookupIfProperty(name = "routing.dynamic.enabled", stringValue = "true")
  public static class Dynamic {

    @Produces
    @ApplicationScoped
    public DiscoveryCacheFactory discoveryCacheFactory(ApplicationManagerService applicationManagerService) {
      return new DiscoveryCacheFactory(applicationManagerService);
    }

    @Produces
    @Named("dynamicRoutingDiscoveryCache")
    @ApplicationScoped
    public AsyncLoadingCache<String, ModuleDiscovery> discoveryCache(DiscoveryCacheFactory factory,
      DynamicRoutingProperties properties) {
      return factory.createCache(properties.discoveryCache());
    }

    @Produces
    @ApplicationScoped
    public DiscoveryCacheUpdator discoveryCacheUpdator(
      @Named("dynamicRoutingDiscoveryCache") AsyncLoadingCache<String, ModuleDiscovery> discoveryCache) {
      return new DiscoveryCacheUpdator(discoveryCache);
    }

    @Produces
    @Named("dynamicLookup")
    @ApplicationScoped
    public RoutingLookup dynamicRoutingLookup(TenantEntitlementService tenantEntitlementService,
      @Named("dynamicRoutingDiscoveryCache") AsyncLoadingCache<String, ModuleDiscovery> discoveryCache) {
      return new DynamicRoutingLookup(tenantEntitlementService, discoveryCache);
    }

    @Produces
    @Named
    @ApplicationScoped
    public ChainedHandler dynamicEgressHandler(@Named("dynamicLookup") RoutingLookup lookup,
      @Named("egressRequestHandler") RoutingEntryHandler handler,
      PathProcessor pathProcessor, ErrorHandler errorHandler) {
      return new RoutingHandlerWithLookup(lookup, handler, pathProcessor, errorHandler);
    }
  }

  @LookupIfProperty(name = "routing.forward-to-gateway.enabled", stringValue = "true")
  public static class Gateway {

    @Produces
    @Named("gatewayLookup")
    @ApplicationScoped
    public RoutingLookup gatewayRoutingLookup(
      @ConfigProperty(name = "routing.forward-to-gateway.destination") String gatewayDestination,
      SidecarProperties sidecarProperties) {
      return new GatewayRoutingLookup(gatewayDestination, sidecarProperties);
    }

    @Produces
    @Named
    @ApplicationScoped
    public ChainedHandler gatewayEgressHandler(@Named("gatewayLookup") RoutingLookup lookup,
      @Named("egressRequestHandler") RoutingEntryHandler handler,
      PathProcessor pathProcessor, ErrorHandler errorHandler) {
      return new RoutingHandlerWithLookup(lookup, handler, pathProcessor, errorHandler);
    }
  }
}
