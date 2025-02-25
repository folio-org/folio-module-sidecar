package org.folio.sidecar.service.routing.lookup;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DiscoveryCacheUpdatorTest {

  @Mock private AsyncLoadingCache<String, ModuleDiscovery> cache;
  @Mock private LoadingCache<String, ModuleDiscovery> syncCache;
  @InjectMocks
  private DiscoveryCacheUpdator discoveryCacheUpdator;

  @Test
  void onDiscovery_positive_moduleIdInCache() {
    String moduleId = "test-module-id";
    when(cache.getIfPresent(moduleId)).thenReturn(completedFuture(mock(ModuleDiscovery.class)));
    when(cache.synchronous()).thenReturn(syncCache);

    discoveryCacheUpdator.onDiscovery(moduleId);

    verify(syncCache).invalidate(moduleId);
    verify(syncCache).refresh(moduleId);
    verifyNoMoreInteractions(cache, syncCache);
  }

  @Test
  void onDiscovery_positive_moduleIdNotInCache() {
    String moduleId = "test-module-id";
    when(cache.getIfPresent(moduleId)).thenReturn(null);

    discoveryCacheUpdator.onDiscovery(moduleId);

    verifyNoMoreInteractions(cache);
  }
}
