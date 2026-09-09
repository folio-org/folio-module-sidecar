package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.REVOKE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.UPGRADE;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.folio.sidecar.configuration.properties.CacheSettings;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantModuleResolverTest {

  private static final String MODULE_NAME = "mod-users-keycloak";
  private static final String TENANT = "testtenant";
  private static final String OTHER_TENANT = "another-tenant";
  private static final String ID_3 = "mod-users-keycloak-3.0.13";
  private static final String ID_4 = "mod-users-keycloak-4.0.2";
  private static final long TTL_MINUTES = 30;

  @Mock private TenantEntitlementService tenantEntitlementService;
  @Mock private AsyncLoadingCache<String, ModuleDiscovery> discoveries;

  private FakeTicker ticker;
  private TenantModuleResolver resolver;

  @BeforeEach
  void setUp() {
    ticker = new FakeTicker();
    resolver = new TenantModuleResolver(tenantEntitlementService, discoveries, cacheSettings(), ticker);
  }

  @Test
  void resolve_positive_cachesBindingAndDiscovery() {
    mockEntitledModule(TENANT, ID_3);
    mockDiscovery(ID_3);

    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);
    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);

    verify(tenantEntitlementService, times(1)).lookupTenantEntitlements(TENANT);
  }

  @Test
  void resolve_negative_moduleNotEntitled() {
    when(tenantEntitlementService.lookupTenantEntitlements(TENANT))
      .thenReturn(succeededFuture(entitlements(1, "mod-foo-1.0.0")));

    var result = resolver.resolve(TENANT, MODULE_NAME);

    assertThat(result.cause())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No entitled module found for name");
  }

  @Test
  void resolve_negative_incompleteEntitlements() {
    when(tenantEntitlementService.lookupTenantEntitlements(TENANT))
      .thenReturn(succeededFuture(entitlements(5, ID_3)));

    var result = resolver.resolve(TENANT, MODULE_NAME);

    assertThat(result.cause())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Incomplete tenant entitlements");
  }

  @Test
  void resolve_negative_blankTenant() {
    var result = resolver.resolve(" ", MODULE_NAME);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).hasMessageContaining("Tenant is required");
  }

  @Test
  void onEntitlementEvent_positive_upgradeUpdatesCachedBinding() {
    mockEntitledModule(TENANT, ID_3);
    mockDiscovery(ID_3);
    mockDiscovery(ID_4);
    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);

    resolver.onEntitlementEvent(event(ID_4, TENANT, UPGRADE));

    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_4);
    verify(tenantEntitlementService, times(1)).lookupTenantEntitlements(TENANT);
  }

  @Test
  void onEntitlementEvent_positive_ignoresRevoke() {
    mockEntitledModule(TENANT, ID_3);
    mockDiscovery(ID_3);
    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);

    resolver.onEntitlementEvent(event(ID_3, TENANT, REVOKE));

    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);
  }

  @Test
  void onEntitlementEvent_positive_ignoresTenantWithoutCachedBinding() {
    resolver.onEntitlementEvent(event(ID_4, OTHER_TENANT, UPGRADE));

    mockEntitledModule(OTHER_TENANT, ID_3);
    mockDiscovery(ID_3);
    assertThat(resolveModuleId(OTHER_TENANT)).isEqualTo(ID_3);
  }

  @Test
  void onEntitlementsChanged_positive_prunesUnservedTenants() {
    when(tenantEntitlementService.lookupTenantEntitlements(TENANT))
      .thenReturn(succeededFuture(entitlements(1, ID_3)), succeededFuture(entitlements(1, ID_4)));
    mockDiscovery(ID_3);
    mockDiscovery(ID_4);
    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);

    resolver.onEntitlementsChanged(EntitlementsEvent.of(Set.of(OTHER_TENANT)));

    // a pruned binding is resolved again, so the second load is what the tenant now gets
    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_4);
  }

  /**
   * The reason expiration exists: no event ever arrives, so only a bounded entry lifetime can bring the sidecar back
   * onto the version the tenant is actually entitled to.
   */
  @Test
  void resolve_positive_missedEventRecoveredAfterExpiration() {
    when(tenantEntitlementService.lookupTenantEntitlements(TENANT))
      .thenReturn(succeededFuture(entitlements(1, ID_3)), succeededFuture(entitlements(1, ID_4)));
    mockDiscovery(ID_3);
    mockDiscovery(ID_4);

    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);
    ticker.advanceMinutes(TTL_MINUTES - 1);
    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_3);

    ticker.advanceMinutes(2);

    assertThat(resolveModuleId(TENANT)).isEqualTo(ID_4);
  }

  private String resolveModuleId(String tenant) {
    var discovery = resolver.resolve(tenant, MODULE_NAME).result();
    return discovery == null ? null : discovery.getId();
  }

  private void mockEntitledModule(String tenant, String moduleId) {
    when(tenantEntitlementService.lookupTenantEntitlements(tenant))
      .thenReturn(succeededFuture(entitlements(1, moduleId)));
  }

  private void mockDiscovery(String moduleId) {
    when(discoveries.get(moduleId))
      .thenReturn(completedFuture(new ModuleDiscovery().id(moduleId).location("http://" + moduleId)));
  }

  private static ResultList<Entitlement> entitlements(int totalRecords, String... modules) {
    var entitlement = new Entitlement();
    entitlement.setModules(List.of(modules));
    return ResultList.of(totalRecords, List.of(entitlement));
  }

  private static TenantEntitlementEvent event(String moduleId, String tenant, TenantEntitlementEvent.Type type) {
    return TenantEntitlementEvent.of(moduleId, tenant, UUID.randomUUID(), type);
  }

  private static CacheSettings cacheSettings() {
    return new CacheSettingsStub(new DurationStub(TTL_MINUTES, TimeUnit.MINUTES));
  }

  private record DurationStub(long duration, TimeUnit unit) implements CacheSettings.Duration {
  }

  private record CacheSettingsStub(CacheSettings.Duration ttl) implements CacheSettings {

    @Override
    public OptionalInt initialCapacity() {
      return OptionalInt.of(2);
    }

    @Override
    public OptionalInt maxSize() {
      return OptionalInt.of(10);
    }

    @Override
    public Optional<Duration> expireAfterWrite() {
      return Optional.of(ttl);
    }

    @Override
    public Optional<Duration> expireAfterAccess() {
      return Optional.empty();
    }
  }

  private static final class FakeTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    void advanceMinutes(long minutes) {
      nanos.addAndGet(TimeUnit.MINUTES.toNanos(minutes));
    }
  }
}
