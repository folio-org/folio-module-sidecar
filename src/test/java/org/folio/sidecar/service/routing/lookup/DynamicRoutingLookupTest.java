package org.folio.sidecar.service.routing.lookup;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.util.Optional.empty;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.MODULE_HINT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.model.ResultList.asSinglePage;
import static org.folio.sidecar.model.ScRoutingEntry.dynamicRoutingEntry;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestValues.moduleDiscovery;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowingConsumer;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.integration.te.TenantEntitlementService;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DynamicRoutingLookupTest {

  private static final String PATH = "/foo/entities";
  private static final String ANOTHER_MODULE_ID = "mod-bar-0.1.0";

  @Mock
  private TenantEntitlementService tenantEntitlementService;
  @Mock
  private AsyncLoadingCache<String, ModuleDiscovery> discoveryCache;
  @InjectMocks
  private DynamicRoutingLookup dynamicRoutingLookup;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(tenantEntitlementService, discoveryCache);
  }

  @Test
  void lookupRoute_positive_noModuleHintHeader() {
    var rc = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(rc.request()).thenReturn(request);
    var headers = mock(MultiMap.class);
    when(request.headers()).thenReturn(headers);
    when(headers.contains(MODULE_HINT)).thenReturn(false);

    var actual = dynamicRoutingLookup.lookupRoute(PATH, rc);

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(empty());
  }

  @Test
  void lookupRoute_positive_moduleHintHeaderIsModuleId() {
    var moduleHint = TestConstants.MODULE_ID;
    var rc = mockModuleHint(moduleHint);
    when(rc.request().method()).thenReturn(GET);

    var md = moduleDiscovery();
    when(discoveryCache.get(moduleHint)).thenReturn(completedFuture(md));

    var actual = dynamicRoutingLookup.lookupRoute(PATH, rc);

    assertThat(actual.succeeded()).isTrue();
    var expected = dynamicEntry(md, rc);
    assertThat(actual.result()).isEqualTo(Optional.of(expected));
  }

  @Test
  void lookupRoute_positive_moduleHintHeaderIsModuleName() {
    var moduleHint = TestConstants.MODULE_NAME;
    var rc = mockModuleHint(moduleHint);
    when(rc.request().method()).thenReturn(GET);
    when(rc.request().headers().get(TENANT)).thenReturn(TENANT_NAME);

    var md = moduleDiscovery();
    when(discoveryCache.get(MODULE_ID)).thenReturn(completedFuture(md));

    when(tenantEntitlementService.getTenantEntitlements(TENANT_NAME, true))
      .thenReturn(succeededFuture(asSinglePage(List.of(
        Entitlement.of(TestConstants.APPLICATION_ID, TENANT_ID, List.of(MODULE_ID, ANOTHER_MODULE_ID))
      ))));

    var actual = dynamicRoutingLookup.lookupRoute(PATH, rc);

    assertThat(actual.succeeded()).isTrue();
    var expected = dynamicEntry(md, rc);
    assertThat(actual.result()).isEqualTo(Optional.of(expected));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void lookupRoute_negative_moduleHintHeaderBlank(String moduleHint) {
    var rc = mockModuleHint(moduleHint);

    var actual = dynamicRoutingLookup.lookupRoute(PATH, rc);

    assertFailed(actual, e -> {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
      assertThat(e.getMessage()).isEqualTo("Module hint header is present but empty: " + MODULE_HINT);
    });
  }

  @Test
  void lookupRoute_negative_moduleIsNotEntitled() {
    var moduleHint = TestConstants.MODULE_NAME;
    var rc = mockModuleHint(moduleHint);
    when(rc.request().method()).thenReturn(GET);
    when(rc.request().headers().get(TENANT)).thenReturn(TENANT_NAME);

    when(tenantEntitlementService.getTenantEntitlements(TENANT_NAME, true))
      .thenReturn(succeededFuture(asSinglePage(List.of(
        Entitlement.of(TestConstants.APPLICATION_ID, TENANT_ID, List.of(ANOTHER_MODULE_ID))
      ))));

    var actual = dynamicRoutingLookup.lookupRoute(PATH, rc);

    assertFailed(actual, e -> {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
      assertThat(e.getMessage()).startsWith("No entitled module found for name");
    });
  }

  @Test
  void lookupRoute_negative_discoveryNotFound() {
    var moduleHint = TestConstants.MODULE_ID;
    var rc = mockModuleHint(moduleHint);
    when(rc.request().method()).thenReturn(GET);

    when(discoveryCache.get(moduleHint)).thenThrow(new RuntimeException("Discovery not found"));

    var actual = dynamicRoutingLookup.lookupRoute(PATH, rc);

    assertFailed(actual, e -> {
      assertThat(e).isInstanceOf(RuntimeException.class);
      assertThat(e.getMessage()).isEqualTo("Discovery not found");
    });
  }

  private static RoutingContext mockModuleHint(String value) {
    var rc = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(rc.request()).thenReturn(request);
    var headers = mock(MultiMap.class);
    when(request.headers()).thenReturn(headers);

    when(headers.contains(MODULE_HINT)).thenReturn(true);
    when(headers.get(MODULE_HINT)).thenReturn(value);

    return rc;
  }

  private static ScRoutingEntry dynamicEntry(ModuleDiscovery md, RoutingContext rc) {
    return dynamicRoutingEntry(md.getLocation(), md.getId(),
      new ModuleBootstrapEndpoint(PATH, rc.request().method().name()));
  }

  private static void assertFailed(Future<Optional<ScRoutingEntry>> actual, ThrowingConsumer<Throwable> assertion) {
    assertThat(actual.failed()).isTrue();
    assertThat(actual.cause()).satisfies(assertion);
  }
}
