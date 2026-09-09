package org.folio.sidecar.integration.users;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestValues.routingContext;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import org.folio.sidecar.exception.ModUsersTargetNotResolvedException;
import org.folio.sidecar.integration.am.model.ModuleDiscovery;
import org.folio.sidecar.integration.users.UserService.PermissionContainer;
import org.folio.sidecar.integration.users.model.User;
import org.folio.sidecar.service.routing.lookup.TenantModuleResolver;
import org.folio.sidecar.service.token.ServiceTokenProvider;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String MOD_USERS_KEYCLOAK = "mod-users-keycloak";
  private static final String TARGET_TENANT = "targetTenant";
  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";
  private static final String MOD_URL = "http://mod-users-keycloak";
  private static final String TOKEN_VALUE = "service-token";

  @InjectMocks private UserService userService;

  @Mock private WebClient webClient;
  @Mock private TenantModuleResolver moduleResolver;
  @Mock private ServiceTokenProvider serviceTokenProvider;
  @Mock private Cache<String, User> cache;
  @Mock private HttpRequest<Buffer> httpRequest;
  @Mock private HttpResponse<Buffer> response;

  @Test
  void findUser_positive_existsInCache() {
    final var user = new User();
    var routingContext = routingContext(TARGET_TENANT);
    var key = USER_ID + "#" + TARGET_TENANT;

    when(cache.getIfPresent(key)).thenReturn(user);
    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isTrue();
    verifyNoInteractions(webClient, moduleResolver);
    verifyNoMoreInteractions(cache);
  }

  @Test
  void findUser_positive_callsTenantResolvedLocation() {
    final var user = new User();
    var routingContext = routingContext(TARGET_TENANT);
    var key = USER_ID + "#" + TARGET_TENANT;

    when(cache.getIfPresent(key)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(TOKEN_VALUE));
    mockResolvedTarget(TARGET_TENANT);
    when(webClient.getAbs(MOD_URL + "/users-keycloak/users/" + USER_ID)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, TOKEN_VALUE)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(200);
    when(response.bodyAsJson(User.class)).thenReturn(user);

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.result()).isSameAs(user);
    verify(cache).put(key, user);
  }

  @Test
  void findUser_negative_moduleNotReachable() {
    var routingContext = routingContext(TARGET_TENANT);

    when(cache.getIfPresent(USER_ID + "#" + TARGET_TENANT)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(TOKEN_VALUE));
    mockResolvedTarget(TARGET_TENANT);
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, TOKEN_VALUE)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(failedFuture("connection reset"));

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.failed()).isTrue();
    verify(httpRequest).send();
  }

  @Test
  void findUser_negative_non200StatusCodeResponse() {
    var routingContext = routingContext(TARGET_TENANT);

    when(cache.getIfPresent(USER_ID + "#" + TARGET_TENANT)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(TOKEN_VALUE));
    mockResolvedTarget(TARGET_TENANT);
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, TOKEN_VALUE)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(400);

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.failed()).isTrue();
  }

  @Test
  void findUser_negative_targetNotResolved() {
    var routingContext = routingContext(TARGET_TENANT);

    when(cache.getIfPresent(USER_ID + "#" + TARGET_TENANT)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(TOKEN_VALUE));
    when(moduleResolver.resolve(TARGET_TENANT, MOD_USERS_KEYCLOAK)).thenReturn(failedFuture("mte is down"));

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.cause()).isInstanceOf(ModUsersTargetNotResolvedException.class);
    verifyNoInteractions(webClient);
  }

  @Test
  void findUserPermissions_positive() {
    final var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url = permissionsUrl();

    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(TOKEN_VALUE));
    mockResolvedTarget(TENANT);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(200);
    when(response.bodyAsJson(PermissionContainer.class)).thenReturn(new PermissionContainer(permissions));

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT);

    assertThat(userPermissions.result()).isEqualTo(permissions);
    verify(webClient).getAbs(url);
  }

  @Test
  void findUserPermissions_negative_400WhilePermissionSearch() {
    final var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url = permissionsUrl();

    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(TOKEN_VALUE));
    mockResolvedTarget(TENANT);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(400);

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT);

    assertThat(userPermissions.failed()).isTrue();
  }

  @Test
  void findUserPermissions_negative_targetNotResolved() {
    var routingContext = routingContext(TENANT_NAME);

    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(TOKEN_VALUE));
    when(moduleResolver.resolve(TENANT, MOD_USERS_KEYCLOAK)).thenReturn(failedFuture("mte is down"));

    var future = userService.findUserPermissions(routingContext, List.of("foo.item.get"), USER_ID, TENANT);

    assertThat(future.cause()).isInstanceOf(ModUsersTargetNotResolvedException.class);
    verifyNoInteractions(webClient);
  }

  private void mockResolvedTarget(String tenant) {
    var discovery = new ModuleDiscovery().id("mod-users-keycloak-3.0.13").location(MOD_URL);
    when(moduleResolver.resolve(tenant, MOD_USERS_KEYCLOAK)).thenReturn(succeededFuture(discovery));
  }

  private static String permissionsUrl() {
    return MOD_URL + "/users-keycloak/users/" + USER_ID + "/permissions"
      + "?desiredPermissions=perm1&desiredPermissions=perm2";
  }
}
