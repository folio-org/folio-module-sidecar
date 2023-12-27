package org.folio.sidecar.integration.users;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestValues.routingContext;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.folio.sidecar.integration.users.configuration.property.ModUsersProperties;
import org.folio.sidecar.integration.users.model.User;
import org.folio.sidecar.service.ServiceTokenProvider;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String TARGET_TENANT = "targetTenant";
  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";

  @InjectMocks private UserService userService;

  @Mock private WebClient webClient;
  @Mock private ModUsersProperties modUsersProperties;
  @Mock private ServiceTokenProvider serviceTokenProvider;
  @Mock private Cache<String, User> cache;
  @Mock private HttpRequest<Buffer> httpRequest;
  @Mock private HttpResponse<Buffer> response;

  @Test
  void findUser_positive_existsInCache() {
    var user = new User();
    var routingContext = routingContext(TARGET_TENANT);
    var key = USER_ID + "#" + TARGET_TENANT;

    when(cache.getIfPresent(key)).thenReturn(user);
    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isTrue();
    verifyNoInteractions(webClient);
    verifyNoMoreInteractions(cache);
  }

  @Test
  void findUser_positive_modUsersInteraction() {
    var user = new User();
    var routingContext = routingContext(TARGET_TENANT);
    var token = "service-token";
    var key = USER_ID + "#" + TARGET_TENANT;

    when(serviceTokenProvider.getServiceToken(routingContext)).thenReturn(succeededFuture(token));
    when(cache.getIfPresent(key)).thenReturn(null);
    when(modUsersProperties.getUrl()).thenReturn("http://mod-users");
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, token)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(200);
    when(response.bodyAsJson(User.class)).thenReturn(user);

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isTrue();
    verify(cache).getIfPresent(key);
    verify(cache).put(key, user);
    verifyNoMoreInteractions(cache, webClient);
  }

  @Test
  void findUser_negative_modUsersInteraction() {
    var routingContext = routingContext(TARGET_TENANT);
    var token = "service-token";
    var key = USER_ID + "#" + TARGET_TENANT;

    when(serviceTokenProvider.getServiceToken(routingContext)).thenReturn(succeededFuture(token));
    when(cache.getIfPresent(key)).thenReturn(null);
    when(modUsersProperties.getUrl()).thenReturn("http://mod-users");
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(TOKEN, token)).thenReturn(httpRequest);
    when(httpRequest.putHeader(TENANT, TARGET_TENANT)).thenReturn(httpRequest);
    when(httpRequest.send()).thenReturn(failedFuture("failed"));

    var future = userService.findUser(TARGET_TENANT, USER_ID, routingContext);

    assertThat(future.succeeded()).isFalse();
    verify(cache).getIfPresent(key);
    verifyNoMoreInteractions(cache, webClient);
  }
}
