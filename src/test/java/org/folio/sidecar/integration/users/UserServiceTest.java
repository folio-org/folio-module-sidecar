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
import org.folio.sidecar.integration.users.UserService.PermissionContainer;
import org.folio.sidecar.integration.users.configuration.property.ModUsersProperties;
import org.folio.sidecar.integration.users.model.User;
import org.folio.sidecar.service.token.ServiceTokenProvider;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String TARGET_TENANT = "targetTenant";
  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";
  private static final String MOD_URL = "http://mod-users-keycloak";

  private UserService userService;

  @Mock private WebClient webClient;
  @Mock private ModUsersProperties modUsersProperties;
  @Mock private ServiceTokenProvider serviceTokenProvider;
  @Mock private Cache<String, User> userCache;
  @Mock private Cache<String, List<String>> permissionsCache;
  @Mock private HttpRequest<Buffer> httpRequest;
  @Mock private HttpResponse<Buffer> response;

  @BeforeEach
  void setUp() {
    userService = new UserService(webClient, modUsersProperties, userCache, permissionsCache, serviceTokenProvider);
  }

  @Test
  void findUser_positive_existsInCache() {
    var user = new User();
    var routingContext = routingContext(TARGET_TENANT);
    var key = USER_ID + "#" + TARGET_TENANT;

    when(userCache.getIfPresent(key)).thenReturn(user);
    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isTrue();
    verifyNoInteractions(webClient);
    verifyNoMoreInteractions(userCache);
  }

  @Test
  void findUser_positive_modUsersInteraction() {
    var user = new User();
    var routingContext = routingContext(TARGET_TENANT);
    var token = "service-token";
    var key = USER_ID + "#" + TARGET_TENANT;

    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(token));
    when(userCache.getIfPresent(key)).thenReturn(null);
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, token)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(200);
    when(response.bodyAsJson(User.class)).thenReturn(user);

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isTrue();
    verify(userCache).getIfPresent(key);
    verify(userCache).put(key, user);
    verifyNoMoreInteractions(userCache, webClient);
  }

  @Test
  void findUser_negative_modUsersInteractionError() {
    var routingContext = routingContext(TARGET_TENANT);
    var token = "service-token";
    var key = USER_ID + "#" + TARGET_TENANT;

    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(token));
    when(userCache.getIfPresent(key)).thenReturn(null);
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, token)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(failedFuture("failed"));

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isFalse();
    verify(userCache).getIfPresent(key);
    verifyNoMoreInteractions(userCache, webClient);
  }

  @Test
  void findUser_negative_non200StatusCodeResponse() {
    var routingContext = routingContext(TARGET_TENANT);
    var token = "service-token";
    var key = USER_ID + "#" + TARGET_TENANT;

    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture(token));
    when(userCache.getIfPresent(key)).thenReturn(null);
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, token)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(400);

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isFalse();
    verify(userCache).getIfPresent(key);
    verifyNoMoreInteractions(userCache, webClient);
  }

  @Test
  void findUserPermissions_positive() {
    var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url =
      MOD_URL + "/users-keycloak/users/" + USER_ID + "/permissions?desiredPermissions=perm1&desiredPermissions=perm2";
    var cacheKey = USER_ID + "#" + TENANT_NAME + "#" + "perm1,perm2";

    when(permissionsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture("service-token"));
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(200);
    when(response.bodyAsJson(PermissionContainer.class)).thenReturn(new PermissionContainer(permissions));

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isTrue();
    assertThat(userPermissions.result()).containsExactly("perm1", "perm2");
    verify(permissionsCache).getIfPresent(cacheKey);
    verify(permissionsCache).put(cacheKey, permissions);
    verify(webClient).getAbs(url);
    verifyNoMoreInteractions(webClient);
  }

  @Test
  void findUserPermissions_positive_cacheHit() {
    var permissions = List.of("perm1", "perm2");
    var cachedResult = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var cacheKey = USER_ID + "#" + TENANT_NAME + "#" + "perm1,perm2";

    when(permissionsCache.getIfPresent(cacheKey)).thenReturn(cachedResult);

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isTrue();
    assertThat(userPermissions.result()).containsExactly("perm1", "perm2");
    verify(permissionsCache).getIfPresent(cacheKey);
    verifyNoInteractions(webClient, serviceTokenProvider);
  }

  @Test
  void findUserPermissions_positive_nullPermissionsNotCached() {
    var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url =
      MOD_URL + "/users-keycloak/users/" + USER_ID + "/permissions?desiredPermissions=perm1&desiredPermissions=perm2";
    var cacheKey = USER_ID + "#" + TENANT_NAME + "#" + "perm1,perm2";

    when(permissionsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture("service-token"));
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(200);
    when(response.bodyAsJson(PermissionContainer.class)).thenReturn(new PermissionContainer(null));

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isTrue();
    assertThat(userPermissions.result()).isNull();
    verify(permissionsCache).getIfPresent(cacheKey);
    verifyNoMoreInteractions(permissionsCache);
  }

  @Test
  void findUserPermissions_negative_400WhilePermissionSearch() {
    var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url =
      MOD_URL + "/users-keycloak/users/" + USER_ID + "/permissions?desiredPermissions=perm1&desiredPermissions=perm2";
    var cacheKey = USER_ID + "#" + TENANT_NAME + "#" + "perm1,perm2";

    when(permissionsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture("service-token"));
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(400);

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isFalse();
    verify(permissionsCache).getIfPresent(cacheKey);
    verify(webClient).getAbs(url);
    verifyNoMoreInteractions(webClient, permissionsCache);
  }

  @Test
  void findUserPermissions_negative_errorWhileSearchingPermissions() {
    var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url =
      MOD_URL + "/users-keycloak/users/" + USER_ID + "/permissions?desiredPermissions=perm1&desiredPermissions=perm2";
    var cacheKey = USER_ID + "#" + TENANT_NAME + "#" + "perm1,perm2";

    when(permissionsCache.getIfPresent(cacheKey)).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture("service-token"));
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(failedFuture("failed"));

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isFalse();
    verify(permissionsCache).getIfPresent(cacheKey);
    verify(webClient).getAbs(url);
    verifyNoMoreInteractions(webClient, permissionsCache);
  }
}
