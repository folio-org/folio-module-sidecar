package org.folio.sidecar.service.routing.lookup;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.CacheSettings;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;

@Log4j2
@RequiredArgsConstructor
public class DiscoveryCacheFactory {

  private final ApplicationManagerService applicationManagerService;

  public AsyncLoadingCache<String, ModuleDiscovery> createCache(CacheSettings properties) {
    var builder = Caffeine.newBuilder();

    properties.initialCapacity().ifPresent(builder::initialCapacity);
    properties.maxSize().ifPresent(builder::maximumSize);
    properties.expireAfterAccess().ifPresent(duration ->
      builder.expireAfterAccess(duration.duration(), duration.unit()));
    properties.expireAfterWrite().ifPresent(duration ->
      builder.expireAfterWrite(duration.duration(), duration.unit()));

    return builder.removalListener(logCachedDiscoveryRemoved())
      .buildAsync(discoveryLoader());
  }

  /**
   * Loads a discovery without blocking a thread, so the cache can be used from request-serving paths.
   *
   * <p>A discovery without a location fails the load on purpose: treating it as a successful value would cache an
   * unusable address until the entry is invalidated or evicted.</p>
   *
   * @return asynchronous discovery loader
   */
  private AsyncCacheLoader<String, ModuleDiscovery> discoveryLoader() {
    return (moduleId, executor) -> applicationManagerService.lookupModuleDiscovery(moduleId)
      .map(discovery -> validated(moduleId, discovery))
      .toCompletionStage()
      .toCompletableFuture();
  }

  private static ModuleDiscovery validated(String moduleId, ModuleDiscovery discovery) {
    if (discovery == null || isBlank(discovery.getLocation())) {
      throw new IllegalStateException("Discovery location is not set: moduleId = " + moduleId);
    }
    return discovery;
  }

  private static RemovalListener<Object, Object> logCachedDiscoveryRemoved() {
    return (key, value, cause) ->
      log.debug("Cached module discovery removed: key = {}, cause = {}", key, cause);
  }
}
