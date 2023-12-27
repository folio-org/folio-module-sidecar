package org.folio.sidecar.service.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import org.folio.sidecar.exception.TenantNotEnabledException;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.TestValues;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

  @Mock private TenantService tenantService;
  @InjectMocks private TenantFilter tenantFilter;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(tenantService);
  }

  @Test
  void filter_positive() {
    var routingContext = TestValues.routingContext(TestConstants.TENANT_NAME);

    when(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).thenReturn(true);

    var result = tenantFilter.applyFilter(routingContext);

    assertThat(result.result()).isEqualTo(routingContext);
    verify(tenantService).isEnabledTenant(TestConstants.TENANT_NAME);
  }

  @Test
  void filter_positive_tenantInstall() {
    var scRoutingEntry = TestValues.scRoutingEntrySysInterface("_tenant", "/_/tenant", HttpMethod.POST);
    var routingContext = TestValues.routingContext(TestConstants.TENANT_NAME, scRoutingEntry);

    var result = tenantFilter.applyFilter(routingContext);

    assertThat(result.result()).isEqualTo(routingContext);
    verifyNoMoreInteractions(tenantService);
  }

  @Test
  void filter_negative_unknownTenant() {
    var routingContext = TestValues.routingContext(TestConstants.TENANT_NAME);

    when(tenantService.isEnabledTenant(TestConstants.TENANT_NAME)).thenReturn(false);

    var result = tenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(TenantNotEnabledException.class)
      .hasMessage("Application is not enabled for tenant: %s", TestConstants.TENANT_NAME);
  }

  @Test
  void getOrder_positive() {
    var result = tenantFilter.getOrder();
    assertEquals(140, result);
  }
}
