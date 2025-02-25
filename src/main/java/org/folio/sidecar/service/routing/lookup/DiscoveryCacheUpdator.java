package org.folio.sidecar.service.routing.lookup;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.integration.kafka.DiscoveryListener;

@Log4j2
@RequiredArgsConstructor
public class DiscoveryCacheUpdator implements DiscoveryListener {

  private final AsyncLoadingCache<String, ModuleDiscovery> cache;

  @Override
  public void onDiscovery(String moduleId) {
    if (cache.getIfPresent(moduleId) != null) {
      var syncCache = cache.synchronous();

      log.debug("Invalidating and refreshing discovery cache entry for module: {}", moduleId);
      // invalidate entry first, otherwise it will be kept in case of an exception during refresh
      syncCache.invalidate(moduleId);
      syncCache.refresh(moduleId);
    }
  }
}
