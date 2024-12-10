package org.folio.sidecar.integration.keycloak.filter;

import static io.vertx.core.Future.succeededFuture;
import static java.util.UUID.randomUUID;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT_ALL;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.SYSTEM_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.SYS_TOKEN;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.USER_ID;
import static org.folio.sidecar.utils.JwtUtils.SESSION_ID_CLAIM;
import static org.folio.sidecar.utils.JwtUtils.USER_ID_CLAIM;
import static org.folio.sidecar.utils.RoutingUtils.PARSED_TOKEN;
import static org.folio.sidecar.utils.RoutingUtils.SC_ROUTING_ENTRY_KEY;
import static org.folio.sidecar.utils.RoutingUtils.SELF_REQUEST_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.ws.rs.ServiceUnavailableException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.KeycloakClient;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakAuthorizationFilterTest extends AbstractFilterTest {

  private static final String KC_PERMISSION = "/foo/entities/{id}#GET";
  private static final String REQUIRED_PERMISSION = "foo.entities.item.get";
  private static final String SESSION_STATE = randomUUID().toString();
  private static final String KC_PERMISSION_NAME_KEY = "kcPermissionName";

  private static final long VALID_TOKEN_EXPIRATION_TIME = Instant.now().plusSeconds(60).getEpochSecond();
  private static final long SYSTEM_TOKEN_EXPIRATION_TIME = Instant.now().plusSeconds(30).getEpochSecond();

  @Mock private KeycloakClient keycloakClient;
  @Mock private HttpResponse<Buffer> userTokenRptResponse;
  @Mock private HttpResponse<Buffer> systemTokenRptResponse;
  @Mock private JsonWebToken userToken;
  @Mock private JsonWebToken systemToken;
  @Mock private Cache<String, JsonWebToken> authTokenCache;

  private KeycloakAuthorizationFilter keycloakAuthorizationFilter;

  @BeforeEach
  void setUp() {
    keycloakAuthorizationFilter = new KeycloakAuthorizationFilter(keycloakClient, authTokenCache);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(keycloakClient, authTokenCache);
  }

  @Test
  void authorize_positive_userToken() {
    prepareUserTokenMocks(false);
    prepareUserRptMocks(SC_OK, succeededFuture(userTokenRptResponse));

    var routingContext = routingContext(scRoutingEntry(), rc -> prepareRoutingContextMocks(rc, userToken, null));
    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);

    verify(authTokenCache).put(userTokenCacheKey(), userToken);
    verify(keycloakClient).evaluatePermissions(TENANT_NAME, KC_PERMISSION, AUTH_TOKEN);
  }

  @Test
  void authorize_positive_userTokenCached() {
    prepareSystemTokenMocks(false);
    prepareUserTokenMocks(true);

    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(PARSED_TOKEN)).thenReturn(userToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemToken);
      when(rc.put(KC_PERMISSION_NAME_KEY, KC_PERMISSION)).thenReturn(rc);
      when(rc.remove(KC_PERMISSION_NAME_KEY)).thenReturn(KC_PERMISSION);
    });

    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verifyNoInteractions(keycloakClient);
    verify(authTokenCache, never()).put(anyString(), any());
  }

  @Test
  void authorize_positive_systemToken() {
    prepareSystemTokenMocks(false);
    prepareSystemRptMocks(SC_OK, succeededFuture(systemTokenRptResponse));

    var routingContext = routingContext(scRoutingEntry(), rc -> prepareRoutingContextMocks(rc, null, systemToken));
    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);

    verify(authTokenCache).put(systemTokenCacheKey(), systemToken);
    verify(keycloakClient).evaluatePermissions(TENANT_NAME, KC_PERMISSION, SYS_TOKEN);
  }

  @Test
  void authorize_positive_systemTokenCached() {
    prepareSystemTokenMocks(true);

    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(PARSED_TOKEN)).thenReturn(userToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemToken);
      when(rc.put(KC_PERMISSION_NAME_KEY, KC_PERMISSION)).thenReturn(rc);
      when(rc.remove(KC_PERMISSION_NAME_KEY)).thenReturn(KC_PERMISSION);
    });

    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verifyNoInteractions(keycloakClient);
    verify(authTokenCache, never()).put(anyString(), any());
  }

  @Test
  void authorize_positive_systemTokenAfterForbiddenForUserToken() {
    prepareUserTokenMocks(false);
    prepareSystemTokenMocks(false);
    prepareSystemRptMocks(SC_OK, succeededFuture(systemTokenRptResponse));

    var routingContext = routingContext(scRoutingEntry(), rc -> prepareRoutingContextMocks(rc, userToken, systemToken));
    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);

    verify(authTokenCache).put(systemTokenCacheKey(), systemToken);
    verify(keycloakClient).evaluatePermissions(TENANT_NAME, KC_PERMISSION, SYS_TOKEN);
  }

  @Test
  void authorize_negative_forbiddenForUserToken() {
    prepareUserTokenMocks(false);
    prepareUserRptMocks(SC_FORBIDDEN, succeededFuture(userTokenRptResponse));

    var routingContext = routingContext(scRoutingEntry(), rc -> prepareRoutingContextMocks(rc, userToken, null));
    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(ForbiddenException.class)
      .hasMessage("Access Denied");

    verify(keycloakClient).evaluatePermissions(TENANT_NAME, KC_PERMISSION, AUTH_TOKEN);
    verify(authTokenCache, never()).put(anyString(), any());
  }

  @Test
  void authorize_negative_rptRequestFailedForUserToken() {
    prepareUserTokenMocks(false);
    var failedResponse = Future.<HttpResponse<Buffer>>failedFuture(new ServiceUnavailableException("Unavailable"));
    when(keycloakClient.evaluatePermissions(TENANT_NAME, KC_PERMISSION, AUTH_TOKEN)).thenReturn(failedResponse);
    when(userToken.getRawToken()).thenReturn(AUTH_TOKEN);

    var routingContext = routingContext(scRoutingEntry(), rc -> prepareRoutingContextMocks(rc, userToken, null));
    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(ForbiddenException.class)
      .hasMessage("Failed to authorize request");

    verify(keycloakClient).evaluatePermissions(TENANT_NAME, KC_PERMISSION, AUTH_TOKEN);
    verify(authTokenCache, never()).put(anyString(), any());
  }

  @Test
  void authorize_negative_rptRequestUnauthorizedForUserToken() {
    prepareUserTokenMocks(false);
    prepareUserRptMocks(SC_UNAUTHORIZED, succeededFuture(userTokenRptResponse));

    var routingContext = routingContext(scRoutingEntry(), rc -> prepareRoutingContextMocks(rc, userToken, null));
    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Unauthorized");

    verify(keycloakClient).evaluatePermissions(TENANT_NAME, KC_PERMISSION, AUTH_TOKEN);
    verify(authTokenCache, never()).put(anyString(), any());
  }

  @Test
  void authorize_negative_rptRequestFailedForSystemToken() {
    prepareSystemTokenMocks(false);
    var failedResponse = Future.<HttpResponse<Buffer>>failedFuture(new ServiceUnavailableException("Unavailable"));
    when(keycloakClient.evaluatePermissions(TENANT_NAME, KC_PERMISSION, SYS_TOKEN)).thenReturn(failedResponse);
    when(systemToken.getRawToken()).thenReturn(SYS_TOKEN);

    var routingContext = routingContext(scRoutingEntry(), rc -> prepareRoutingContextMocks(rc, null, systemToken));
    var result = keycloakAuthorizationFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(ForbiddenException.class)
      .hasMessage("Failed to authorize request");

    verify(keycloakClient).evaluatePermissions(TENANT_NAME, KC_PERMISSION, SYS_TOKEN);
    verify(authTokenCache, never()).put(anyString(), any());
  }

  @Test
  void shouldSkip_positive() {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry());

    var result = keycloakAuthorizationFilter.shouldSkip(routingContext);

    assertThat(result).isFalse();
  }

  @Test
  void shouldSkip_positive_systemRequest() {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry("system", REQUIRED_PERMISSION));

    var result = keycloakAuthorizationFilter.shouldSkip(routingContext);

    assertThat(result).isTrue();
  }

  @Test
  void shouldSkip_noPermissionsRequired() {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry("not-system"));

    var result = keycloakAuthorizationFilter.shouldSkip(routingContext);

    assertThat(result).isTrue();
  }

  @Test
  void shouldSkip_selfRequest() {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry("not-system", REQUIRED_PERMISSION));
    when(routingContext.get(SELF_REQUEST_KEY)).thenReturn(true);

    var result = keycloakAuthorizationFilter.shouldSkip(routingContext);

    assertThat(result).isTrue();
  }

  @Test
  void shouldSkip_negative_timerEndpoint() {
    var routingContext = routingContext(scRoutingEntryWithId("system", "_timer"), rc -> {});
    var actual = keycloakAuthorizationFilter.shouldSkip(routingContext);
    assertThat(actual).isFalse();
  }

  @Test
  void invalidate_positive_logoutEvent() {
    var mockedToken = mock(JsonWebToken.class);
    var cache = new ConcurrentHashMap<>(Map.of(
      systemTokenCacheKey(), mockedToken,
      userTokenCacheKey(USER_ID, "wrong-sid"), mockedToken,
      userTokenCacheKey(USER_ID, SESSION_STATE), mockedToken,
      "testkey", mockedToken
    ));
    when(authTokenCache.asMap()).thenReturn(cache);

    var event = LogoutEvent.of(USER_ID, SESSION_STATE, null, LOGOUT);
    keycloakAuthorizationFilter.invalidate(event);

    assertThat(cache.get(userTokenCacheKey())).isNull();
    assertThat(cache.size()).isEqualTo(3);
    verify(authTokenCache).asMap();
  }

  @Test
  void invalidate_positive_logoutAllEvent() {
    var mockedToken = mock(JsonWebToken.class);
    var keyForInvalidating1 = userTokenCacheKey(USER_ID, "wrong-sid");
    var keyForInvalidating2 = userTokenCacheKey(USER_ID, SESSION_STATE);
    var cache = new ConcurrentHashMap<>(Map.of(
      systemTokenCacheKey(), mockedToken,
      keyForInvalidating1, mockedToken,
      keyForInvalidating2, mockedToken,
      "testkey", mockedToken
    ));
    when(authTokenCache.asMap()).thenReturn(cache);

    var event = LogoutEvent.of(USER_ID, null, null, LOGOUT_ALL);
    keycloakAuthorizationFilter.invalidate(event);

    assertThat(cache.get(keyForInvalidating1)).isNull();
    assertThat(cache.get(keyForInvalidating2)).isNull();
    assertThat(cache.size()).isEqualTo(2);
    verify(authTokenCache).asMap();
  }

  private void prepareUserTokenMocks(boolean cached) {
    when(userToken.getExpirationTime()).thenReturn(VALID_TOKEN_EXPIRATION_TIME);
    when(userToken.containsClaim(USER_ID_CLAIM)).thenReturn(true);
    when(userToken.getClaim(USER_ID_CLAIM)).thenReturn(USER_ID);
    when(userToken.containsClaim(SESSION_ID_CLAIM)).thenReturn(true);
    when(userToken.getClaim(SESSION_ID_CLAIM)).thenReturn(SESSION_STATE);
    when(authTokenCache.getIfPresent(userTokenCacheKey())).thenReturn(cached ? userToken : null);
  }

  private void prepareSystemTokenMocks(boolean cached) {
    when(systemToken.getExpirationTime()).thenReturn(SYSTEM_TOKEN_EXPIRATION_TIME);
    when(systemToken.containsClaim(USER_ID_CLAIM)).thenReturn(false);
    when(systemToken.containsClaim(SESSION_ID_CLAIM)).thenReturn(false);
    when(authTokenCache.getIfPresent(systemTokenCacheKey())).thenReturn(cached ? systemToken : null);
  }

  private static void prepareRoutingContextMocks(RoutingContext rc, JsonWebToken userToken, JsonWebToken systemToken) {
    when(rc.get(PARSED_TOKEN)).thenReturn(userToken);
    when(rc.get(SYSTEM_TOKEN)).thenReturn(systemToken);
    when(rc.put(KC_PERMISSION_NAME_KEY, KC_PERMISSION)).thenReturn(rc);
    when(rc.get(KC_PERMISSION_NAME_KEY)).thenReturn(KC_PERMISSION);
    when(rc.remove(KC_PERMISSION_NAME_KEY)).thenReturn(KC_PERMISSION);
    when(rc.request().getHeader(TENANT)).thenReturn(TENANT_NAME);
  }

  private void prepareUserRptMocks(int rptResponseStatus, Future<HttpResponse<Buffer>> rptFuture) {
    when(keycloakClient.evaluatePermissions(TENANT_NAME, KC_PERMISSION, AUTH_TOKEN)).thenReturn(rptFuture);
    when(userTokenRptResponse.statusCode()).thenReturn(rptResponseStatus);
    when(userToken.getRawToken()).thenReturn(AUTH_TOKEN);
  }

  private void prepareSystemRptMocks(int rptResponseStatus, Future<HttpResponse<Buffer>> rptFuture) {
    when(keycloakClient.evaluatePermissions(TENANT_NAME, KC_PERMISSION, SYS_TOKEN)).thenReturn(rptFuture);
    when(systemTokenRptResponse.statusCode()).thenReturn(rptResponseStatus);
    when(systemToken.getRawToken()).thenReturn(SYS_TOKEN);
  }

  private static RoutingContext routingContext(ScRoutingEntry re, Consumer<RoutingContext> modifier) {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(re);
    when(routingContext.get(SELF_REQUEST_KEY)).thenReturn(false);
    when(routingContext.request().method()).thenReturn(HttpMethod.GET);
    lenient().when(routingContext.request().getHeader(TENANT)).thenReturn(TENANT_NAME);
    modifier.accept(routingContext);
    return routingContext;
  }

  private static String userTokenCacheKey() {
    return String.format("%s#%s#%s#%s#%s", KC_PERMISSION, TENANT_NAME, USER_ID, SESSION_STATE,
      VALID_TOKEN_EXPIRATION_TIME);
  }

  private static String userTokenCacheKey(String userId, String sid) {
    return String.format("%s#%s#%s#%s#%s", KC_PERMISSION, TENANT_NAME, userId, sid,
      VALID_TOKEN_EXPIRATION_TIME);
  }

  private static String systemTokenCacheKey() {
    return String.format("%s#%s#%s", KC_PERMISSION, TENANT_NAME, SYSTEM_TOKEN_EXPIRATION_TIME);
  }

  protected ModuleBootstrapEndpoint moduleBootstrapEndpoint() {
    return new ModuleBootstrapEndpoint("/foo/entities/{id}", "GET");
  }
}
