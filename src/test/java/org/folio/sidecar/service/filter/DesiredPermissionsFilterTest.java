package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.USER_ID;
import static org.folio.sidecar.support.TestValues.routingContext;
import static org.folio.sidecar.utils.RoutingUtils.getScRoutingEntry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import java.util.List;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.integration.users.UserService;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredPermissionsFilterTest {

  @InjectMocks private DesiredPermissionsFilter filter;

  @Mock private UserService userService;
  @Mock private HttpServerRequest request;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(userService);
  }

  @Test
  void filter_positive_findingFailed() {
    var rc = routingContext(TENANT_NAME);
    rc.request().headers().add(OkapiHeaders.USER_ID, USER_ID);
    var permissionsDesired = List.of("perm1", "perm2");
    var headers = caseInsensitiveMultiMap()
      .add(OkapiHeaders.USER_ID, USER_ID)
      .add(OkapiHeaders.TENANT, TENANT_NAME);
    getScRoutingEntry(rc).getRoutingEntry().setPermissionsDesired(permissionsDesired);

    when(userService.findUserPermissions(rc, permissionsDesired, USER_ID, TENANT_NAME))
      .thenReturn(failedFuture("Error"));
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TENANT_NAME);
    when(request.headers()).thenReturn(headers);

    var resultFuture = filter.filter(rc);

    assertThat(resultFuture.succeeded()).isTrue();
    verify(userService).findUserPermissions(rc, permissionsDesired, USER_ID, TENANT_NAME);
  }

  @Test
  void filter_positive_userIdNotFound() {
    var rc = routingContext(TENANT_NAME);
    var resultFuture = filter.filter(rc);

    assertThat(resultFuture.succeeded()).isTrue();
    verifyNoInteractions(userService);
  }

  @Test
  void filter_positive_permissionsDesiredNotFound() {
    var rc = routingContext(TENANT_NAME);
    rc.request().headers().add(OkapiHeaders.USER_ID, USER_ID);
    var resultFuture = filter.filter(rc);

    assertThat(resultFuture.succeeded()).isTrue();
    verifyNoInteractions(userService);
  }

  @Test
  void filter_positive_permissionsHeaderRemoved() {
    var rc = routingContext(TENANT_NAME);
    rc.request().headers().add(OkapiHeaders.USER_ID, USER_ID);
    rc.request().headers().add(OkapiHeaders.PERMISSIONS, "perm1,perm2");

    var resultFuture = filter.filter(rc);

    assertThat(resultFuture.succeeded()).isTrue();
    assertThat(rc.request().headers().contains(OkapiHeaders.PERMISSIONS)).isFalse();
    verifyNoInteractions(userService);
  }
}
