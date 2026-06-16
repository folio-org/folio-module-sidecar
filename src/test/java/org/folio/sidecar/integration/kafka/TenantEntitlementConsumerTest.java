package org.folio.sidecar.integration.kafka;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.lookup.TenantEgressRoutingService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantEntitlementConsumerTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String TENANT = "tenant1";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Mock TenantService tenantService;
  @Mock TenantEgressRoutingService tenantEgressRoutingService;

  private TenantEntitlementConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new TenantEntitlementConsumer(tenantService, tenantEgressRoutingService);
  }

  @Test
  void consume_entitleEvent_enablesTenantAndRefreshes() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    when(tenantEgressRoutingService.refreshTenant(TENANT)).thenReturn(succeededFuture());

    var stage = consumer.consume(event(Type.ENTITLE)).toCompletableFuture();

    assertThat(stage.isDone()).isTrue();
    assertThat(stage.isCompletedExceptionally()).isFalse();
    verify(tenantService).enableTenant(TENANT);
    verify(tenantEgressRoutingService).refreshTenant(TENANT);
  }

  @Test
  void consume_revokeEvent_disablesTenantAndRefreshes() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    when(tenantEgressRoutingService.refreshTenant(TENANT)).thenReturn(succeededFuture());

    var stage = consumer.consume(event(Type.REVOKE)).toCompletableFuture();

    assertThat(stage.isCompletedExceptionally()).isFalse();
    verify(tenantService).disableTenant(TENANT);
    verify(tenantEgressRoutingService).refreshTenant(TENANT);
  }

  @Test
  void consume_refreshFails_acknowledgesAndDoesNotNack() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(true);
    when(tenantEgressRoutingService.refreshTenant(TENANT))
      .thenReturn(failedFuture(new RuntimeException("bootstrap unavailable")));

    var stage = consumer.consume(event(Type.ENTITLE)).toCompletableFuture();

    assertThat(stage.isDone()).isTrue();
    assertThat(stage.isCompletedExceptionally()).isFalse();
    verify(tenantEgressRoutingService).refreshTenant(TENANT);
  }

  @Test
  void consume_notAssignedModule_acknowledgesWithoutSideEffects() {
    when(tenantService.isAssignedModule(MODULE_ID)).thenReturn(false);

    var stage = consumer.consume(event(Type.ENTITLE)).toCompletableFuture();

    assertThat(stage.isCompletedExceptionally()).isFalse();
    verify(tenantService, never()).enableTenant(any());
    verify(tenantService, never()).disableTenant(any());
    verify(tenantEgressRoutingService, never()).refreshTenant(any());
  }

  private static TenantEntitlementEvent event(Type type) {
    return TenantEntitlementEvent.of(MODULE_ID, TENANT, TENANT_ID, type);
  }
}
