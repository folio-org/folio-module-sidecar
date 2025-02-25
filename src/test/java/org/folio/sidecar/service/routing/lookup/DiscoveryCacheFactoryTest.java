package org.folio.sidecar.service.routing.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.folio.sidecar.configuration.properties.CacheSettings;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DiscoveryCacheFactoryTest {

  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private CacheSettings cacheSettings;
  @InjectMocks
  private DiscoveryCacheFactory discoveryCacheFactory;

  @Test
  void createCache_positive_withAllProperties() {
    try (var mockedStatic = mockStatic(Caffeine.class)) {
      var caffeine = mock(Caffeine.class);
      mockedStatic.when(Caffeine::newBuilder).thenReturn(caffeine);

      when(cacheSettings.initialCapacity()).thenReturn(OptionalInt.of(100));
      when(cacheSettings.maxSize()).thenReturn(OptionalInt.of(1000));
      when(cacheSettings.expireAfterAccess()).thenReturn(Optional.of(new DurationMock(1, TimeUnit.SECONDS)));
      when(cacheSettings.expireAfterWrite()).thenReturn(Optional.of(new DurationMock(1, TimeUnit.SECONDS)));
      var result = mock(AsyncLoadingCache.class);
      when(caffeine.removalListener(any())).thenReturn(caffeine);
      when(caffeine.buildAsync(any())).thenReturn(result);

      AsyncLoadingCache<String, ModuleDiscovery> cache = discoveryCacheFactory.createCache(cacheSettings);

      assertThat(cache).isEqualTo(result);
      verify(caffeine).initialCapacity(100);
      verify(caffeine).maximumSize(1000);
      verify(caffeine).expireAfterAccess(1, TimeUnit.SECONDS);
      verify(caffeine).expireAfterWrite(1, TimeUnit.SECONDS);
      verifyNoMoreInteractions(cache, cacheSettings, applicationManagerService);
    }
  }

  @Test
  void createCache_positive_withNoProperties() {
    try (var mockedStatic = mockStatic(Caffeine.class)) {
      var caffeine = mock(Caffeine.class);
      mockedStatic.when(Caffeine::newBuilder).thenReturn(caffeine);

      when(cacheSettings.initialCapacity()).thenReturn(OptionalInt.empty());
      when(cacheSettings.maxSize()).thenReturn(OptionalInt.empty());
      when(cacheSettings.expireAfterAccess()).thenReturn(Optional.empty());
      when(cacheSettings.expireAfterWrite()).thenReturn(Optional.empty());
      var result = mock(AsyncLoadingCache.class);
      when(caffeine.removalListener(any())).thenReturn(caffeine);
      when(caffeine.buildAsync(any())).thenReturn(result);

      AsyncLoadingCache<String, ModuleDiscovery> cache = discoveryCacheFactory.createCache(cacheSettings);

      assertThat(cache).isEqualTo(result);
      verifyNoMoreInteractions(cache, cacheSettings, applicationManagerService);
    }
  }

  private record DurationMock(long duration, TimeUnit unit) implements CacheSettings.Duration {
  }
}
