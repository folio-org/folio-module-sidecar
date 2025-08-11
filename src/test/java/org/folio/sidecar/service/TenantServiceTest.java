package org.folio.sidecar.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.mutiny.core.eventbus.EventBus;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.te.TenantEntitlementClient;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.integration.tm.TenantManagerClient;
import org.folio.sidecar.integration.tm.model.Tenant;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.model.ResultList;
import org.folio.sidecar.service.token.ServiceTokenProvider;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

  @Mock private ServiceTokenProvider tokenProvider;
  @Mock private RetryTemplate retryTemplate;
  @Mock private TenantManagerClient tenantManagerClient;
  @Mock private TenantEntitlementClient tenantEntitlementClient;
  @Mock private ModuleProperties moduleProperties;
  @Mock private EventBus eventBus;

  @InjectMocks private TenantService tenantService;

  @Test
  void init_positive() {
    mockRetryTemplate();
    when(moduleProperties.getId()).thenReturn(TestConstants.MODULE_ID);
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(TestConstants.AUTH_TOKEN));
    when(tenantEntitlementClient.getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(
        ResultList.asSinglePage(Entitlement.of(TestConstants.APPLICATION_ID, TestConstants.TENANT_ID, emptyList()))));

    var tenant = Tenant.of(TestConstants.TENANT_UUID, TestConstants.TENANT_NAME, "tenant description");
    when(tenantManagerClient.getTenantInfo(List.of(TestConstants.TENANT_ID), TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(List.of(tenant)));

    tenantService.init();

    assertThat(tenantService.isAssignedModule(TestConstants.MODULE_ID)).isTrue();
    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isTrue();
    verify(eventBus).publish(eq(EntitlementsEvent.ENTITLEMENTS_EVENT), any(EntitlementsEvent.class));
  }

  @Test
  void init_negative_FailedToLoadEntitlements() {
    mockRetryTemplate();
    when(moduleProperties.getId()).thenReturn(TestConstants.MODULE_ID);
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(TestConstants.AUTH_TOKEN));
    when(tenantEntitlementClient.getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
      .thenReturn(failedFuture(new RuntimeException()));

    tenantService.init();

    verifyNoInteractions(tenantManagerClient);

    assertThat(tenantService.isAssignedModule(TestConstants.MODULE_ID)).isTrue();
    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isFalse();
  }

  @Test
  void init_negative_FailedToLoadTenants() {
    mockRetryTemplate();
    when(moduleProperties.getId()).thenReturn(TestConstants.MODULE_ID);
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(TestConstants.AUTH_TOKEN));
    when(tenantEntitlementClient.getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(
        ResultList.asSinglePage(Entitlement.of(TestConstants.APPLICATION_ID, TestConstants.TENANT_ID, emptyList()))));
    when(tenantManagerClient.getTenantInfo(List.of(TestConstants.TENANT_ID), TestConstants.AUTH_TOKEN)).thenReturn(
      failedFuture(new RuntimeException()));

    tenantService.init();

    assertThat(tenantService.isAssignedModule(TestConstants.MODULE_ID)).isTrue();
    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isFalse();
  }

  @Test
  void isEnabledTenant_negative_tenantAndEntitlementNotLoaded() {
    var result = tenantService.isEnabledTenant(TestConstants.TENANT_NAME);

    Assertions.assertFalse(result);
  }

  @Test
  void enableAndThenDisableTenant_positive() {
    var inOrder = inOrder(eventBus);
    // enable
    tenantService.enableTenant(TestConstants.TENANT_NAME);

    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isTrue();
    inOrder.verify(eventBus).publish(EntitlementsEvent.ENTITLEMENTS_EVENT, tenantEntitlementsEvent());

    // disable
    tenantService.disableTenant(TestConstants.TENANT_NAME);

    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isFalse();
    inOrder.verify(eventBus).publish(EntitlementsEvent.ENTITLEMENTS_EVENT, emptyEntitlementsEvent());
  }

  @Test
  void enableTenant_negative_alreadyEnabled() {
    tenantService.enableTenant(TestConstants.TENANT_NAME);
    tenantService.enableTenant(TestConstants.TENANT_NAME);

    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isTrue();
    verify(eventBus, only()).publish(EntitlementsEvent.ENTITLEMENTS_EVENT, tenantEntitlementsEvent());
  }

  @Test
  void disableTenant_negative_notEnabled() {
    tenantService.disableTenant(TestConstants.TENANT_NAME);

    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isFalse();
    verifyNoInteractions(eventBus);
  }

  @Test
  void executeTenantsAndEntitlementsTask_positive() {
    mockRetryTemplate();
    when(moduleProperties.getId()).thenReturn(TestConstants.MODULE_ID);
    // at first call, tokenProvider returns failed future, then succeeded future
    when(tokenProvider.getAdminToken())
      .thenReturn(failedFuture("Failed message"))
      .thenReturn(succeededFuture(TestConstants.AUTH_TOKEN));
    when(tenantEntitlementClient.getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(
        ResultList.asSinglePage(Entitlement.of(TestConstants.APPLICATION_ID, TestConstants.TENANT_ID, emptyList()))));

    var tenant = Tenant.of(TestConstants.TENANT_UUID, TestConstants.TENANT_NAME, "tenant description");
    when(tenantManagerClient.getTenantInfo(List.of(TestConstants.TENANT_ID), TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(List.of(tenant)));

    tenantService.init();
    tenantService.resetTaskFlag();
    tenantService.executeTenantsAndEntitlementsTask();

    assertThat(tenantService.isAssignedModule(TestConstants.MODULE_ID)).isTrue();
    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isTrue();
    verify(eventBus).publish(eq(EntitlementsEvent.ENTITLEMENTS_EVENT), any(EntitlementsEvent.class));
  }

  @Test
  void executeTenantsAndEntitlementsTask_noop_whenCronFlagFalse() {

    // Without resetting the cron flag, the task should not start
    tenantService.executeTenantsAndEntitlementsTask();

    verifyNoInteractions(tokenProvider, tenantEntitlementClient, tenantManagerClient, eventBus);
  }

  @Test
  void executeTenantsAndEntitlementsTask_startsEvenIfPreviousTaskIncomplete() {
    mockRetryTemplate();
    when(moduleProperties.getId()).thenReturn(TestConstants.MODULE_ID);
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(TestConstants.AUTH_TOKEN));

    // First entitlement call never completes (simulates stuck previous task)
    var firstCallPromise = Promise.<ResultList<Entitlement>>promise();
    when(tenantEntitlementClient.getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
      .thenReturn(firstCallPromise.future())
      .thenReturn(succeededFuture(
        ResultList.asSinglePage(Entitlement.of(TestConstants.APPLICATION_ID, TestConstants.TENANT_ID, emptyList()))));

    var tenant = Tenant.of(TestConstants.TENANT_UUID, TestConstants.TENANT_NAME, "tenant description");
    when(tenantManagerClient.getTenantInfo(List.of(TestConstants.TENANT_ID), TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(List.of(tenant)));

    // init kicks off the first (stuck) load
    tenantService.init();

    // cron flips the flag, and the next failed check triggers a new load
    tenantService.resetTaskFlag();
    tenantService.executeTenantsAndEntitlementsTask();

    // Second load should complete and enable tenant
    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isTrue();
    verify(tenantEntitlementClient, times(2))
      .getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN);
    verify(eventBus).publish(eq(EntitlementsEvent.ENTITLEMENTS_EVENT), any(EntitlementsEvent.class));
  }

  @Test
  void executeTenantsAndEntitlementsTask_onlyOneRefreshPerCronReset() {
    mockRetryTemplate();
    when(moduleProperties.getId()).thenReturn(TestConstants.MODULE_ID);
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(TestConstants.AUTH_TOKEN));
    when(tenantEntitlementClient.getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(
        ResultList.asSinglePage(Entitlement.of(TestConstants.APPLICATION_ID, TestConstants.TENANT_ID, emptyList()))));

    var tenant = Tenant.of(TestConstants.TENANT_UUID, TestConstants.TENANT_NAME, "tenant description");
    when(tenantManagerClient.getTenantInfo(List.of(TestConstants.TENANT_ID), TestConstants.AUTH_TOKEN))
      .thenReturn(succeededFuture(List.of(tenant)));

    // First allowed execution
    tenantService.resetTaskFlag();
    tenantService.executeTenantsAndEntitlementsTask();

    // Second call without cron reset must be a no-op
    tenantService.executeTenantsAndEntitlementsTask();

    verify(tenantEntitlementClient, times(1))
      .getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN);
    assertThat(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).isTrue();

    // After cron reset, another execution should be allowed
    tenantService.resetTaskFlag();
    tenantService.executeTenantsAndEntitlementsTask();

    verify(tenantEntitlementClient, times(2))
      .getModuleEntitlements(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN);
  }

  @Test
  void isEnabledTenant_negative_nullOrEmpty() {
    Assertions.assertFalse(tenantService.isEnabledTenant(null));
    Assertions.assertFalse(tenantService.isEnabledTenant(""));
  }

  @SuppressWarnings("unchecked")
  private void mockRetryTemplate() {
    when(retryTemplate.callAsync(any(Supplier.class))).thenAnswer(invocation -> {
      Supplier<Future<List<String>>> supplier = invocation.getArgument(0);
      return supplier.get();
    });
  }

  private static EntitlementsEvent tenantEntitlementsEvent() {
    return EntitlementsEvent.of(Set.of(TestConstants.TENANT_NAME));
  }

  private static EntitlementsEvent emptyEntitlementsEvent() {
    return EntitlementsEvent.of(Collections.emptySet());
  }
}
