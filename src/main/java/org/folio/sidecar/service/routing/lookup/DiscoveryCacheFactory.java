package org.folio.sidecar.service.routing.lookup;

import static org.folio.sidecar.utils.FutureUtils.executeAndGet;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
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

  private CacheLoader<String, ModuleDiscovery> discoveryLoader() {
    return moduleId -> {
      var discovery = applicationManagerService.getModuleDiscovery(moduleId);
      return executeAndGet(discovery);
    };
  }

  private static RemovalListener<Object, Object> logCachedDiscoveryRemoved() {
    return (key, value, cause) ->
      log.debug("Cached module discovery removed: key = {}, cause = {}", key, cause);
  }
}
