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
import java.util.concurrent.ConcurrentHashMap;
import org.folio.sidecar.integration.kafka.LogoutEvent;
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
  @Mock private Cache<String, List<String>> permissionCache;
  @Mock private HttpRequest<Buffer> httpRequest;
  @Mock private HttpResponse<Buffer> response;

  @BeforeEach
  void setUp() {
    userService = new UserService(webClient, modUsersProperties, userCache, permissionCache, serviceTokenProvider);
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

    when(permissionCache.getIfPresent(anyString())).thenReturn(null);
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
    verify(permissionCache).getIfPresent(anyString());
    verify(permissionCache).put(anyString(), eq(permissions));
    verify(webClient).getAbs(url);
    verifyNoMoreInteractions(webClient);
  }

  @Test
  void findUserPermissions_positive_cacheHit() {
    var permissions = List.of("perm1", "perm2");
    var cachedPermissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);

    when(permissionCache.getIfPresent(anyString())).thenReturn(cachedPermissions);

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isTrue();
    assertThat(userPermissions.result()).isEqualTo(cachedPermissions);
    verify(permissionCache).getIfPresent(anyString());
    verifyNoInteractions(webClient, serviceTokenProvider);
  }

  @Test
  void findUserPermissions_negative_400WhilePermissionSearch() {
    var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url =
      MOD_URL + "/users-keycloak/users/" + USER_ID + "/permissions?desiredPermissions=perm1&desiredPermissions=perm2";

    when(permissionCache.getIfPresent(anyString())).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture("service-token"));
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(400);

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isFalse();
    verify(permissionCache).getIfPresent(anyString());
    verify(webClient).getAbs(url);
    verifyNoMoreInteractions(webClient);
  }

  @Test
  void findUserPermissions_negative_errorWhileSearchingPermissions() {
    var permissions = List.of("perm1", "perm2");
    var routingContext = routingContext(TENANT_NAME);
    var url =
      MOD_URL + "/users-keycloak/users/" + USER_ID + "/permissions?desiredPermissions=perm1&desiredPermissions=perm2";

    when(permissionCache.getIfPresent(anyString())).thenReturn(null);
    when(serviceTokenProvider.getToken(routingContext)).thenReturn(succeededFuture("service-token"));
    when(modUsersProperties.getUrl()).thenReturn(MOD_URL);
    when(webClient.getAbs(url)).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TENANT), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(TOKEN), anyString())).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(failedFuture("failed"));

    var userPermissions = userService.findUserPermissions(routingContext, permissions, USER_ID, TENANT_NAME);

    assertThat(userPermissions.succeeded()).isFalse();
    verify(permissionCache).getIfPresent(anyString());
    verify(webClient).getAbs(url);
    verifyNoMoreInteractions(webClient);
  }

  @Test
  void invalidate_positive() {
    var userCacheMap = new ConcurrentHashMap<String, User>();
    userCacheMap.put(USER_ID + "#tenant1", new User());
    userCacheMap.put("other-user#tenant1", new User());

    var permCacheMap = new ConcurrentHashMap<String, List<String>>();
    permCacheMap.put("tenant1#" + USER_ID + "#12345", List.of("perm1"));
    permCacheMap.put("tenant1#other-user#12345", List.of("perm2"));

    when(userCache.asMap()).thenReturn(userCacheMap);
    when(permissionCache.asMap()).thenReturn(permCacheMap);

    var event = LogoutEvent.of(USER_ID, "sessionId", "keycloakUserId", LogoutEvent.Type.LOGOUT);
    userService.invalidate(event);

    assertThat(userCacheMap).hasSize(1);
    assertThat(userCacheMap).containsKey("other-user#tenant1");
    assertThat(permCacheMap).hasSize(1);
    assertThat(permCacheMap).containsKey("tenant1#other-user#12345");
  }
}
