package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.PERMISSIONS;
import static org.folio.sidecar.utils.RoutingUtils.SC_ROUTING_ENTRY_KEY;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ModulePermissionsService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModulePermissionPopulatorTest {

  @InjectMocks ModulePermissionPopulator modulePermissionPopulator;

  @Mock ModulePermissionsService modulePermissionsService;
  @Mock RoutingContext routingContext;
  @Mock ScRoutingEntry scRoutingEntry;
  @Mock ModuleBootstrapEndpoint moduleBootstrapEndpoint;
  @Mock HttpServerRequest request;
  @Mock MultiMap headers;

  @Test
  void filter_positive_shouldSkipWhenNoPermissionsDesired() {
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry);
    when(scRoutingEntry.getRoutingEntry()).thenReturn(moduleBootstrapEndpoint);
    when(moduleBootstrapEndpoint.getPermissionsDesired()).thenReturn(List.of());

    var result = modulePermissionPopulator.filter(routingContext);

    assertThat(result.succeeded()).isTrue();
    verifyNoInteractions(modulePermissionsService);
  }

  @Test
  void filter_positive_addPermissionsHeader() {
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry);
    when(scRoutingEntry.getRoutingEntry()).thenReturn(moduleBootstrapEndpoint);
    when(moduleBootstrapEndpoint.getPermissionsDesired()).thenReturn(List.of("perm1", "perm2"));
    when(modulePermissionsService.getPermissions()).thenReturn(succeededFuture(List.of("perm1", "perm2", "perm3")));
    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);

    var result = modulePermissionPopulator.filter(routingContext);

    assertThat(result.succeeded()).isTrue();
    verify(routingContext.request().headers()).set(PERMISSIONS, "[\"perm1\",\"perm2\"]");
  }

  @Test
  void filter_positive_handleEmptyModulePermissions() {
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry);
    when(scRoutingEntry.getRoutingEntry()).thenReturn(moduleBootstrapEndpoint);
    when(moduleBootstrapEndpoint.getPermissionsDesired()).thenReturn(List.of("perm1", "perm2"));
    when(modulePermissionsService.getPermissions()).thenReturn(succeededFuture(List.of()));
    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);

    var result = modulePermissionPopulator.filter(routingContext);

    assertThat(result.succeeded()).isTrue();
    verifyNoInteractions(routingContext.request().headers());
  }
}
