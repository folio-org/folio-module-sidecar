package org.folio.sidecar.integration.keycloak;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.join;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT_ALL;
import static org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse.INACTIVE_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.USER_ID;
import static org.folio.sidecar.utils.JwtUtils.SESSION_ID_CLAIM;
import static org.folio.sidecar.utils.JwtUtils.USER_ID_CLAIM;
import static org.folio.sidecar.utils.RoutingUtils.ORIGIN_TENANT;
import static org.folio.sidecar.utils.RoutingUtils.PARSED_TOKEN;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.cred.CredentialService;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.model.TokenIntrospectionResponse;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IntrospectionServiceTest {

  private static final String JWT = "jwt";

  @InjectMocks private IntrospectionService introspectionService;

  @Mock private Cache<String, TokenIntrospectionResponse> tokenCache;
  @Mock private CredentialService credentialService;
  @Mock private KeycloakClient keycloakClient;
  @Mock private HttpResponse<Buffer> introspectionResponse;

  @Test
  void invalidate_positive_logoutEventType() {
    var sessionId = "session1";
    var key = cacheKey(TENANT_NAME, USER_ID, sessionId);

    var cache = new ConcurrentHashMap<>(Map.of(key, activeTokenResponse()));
    when(tokenCache.asMap()).thenReturn(cache);

    introspectionService.invalidate(LogoutEvent.of(USER_ID, sessionId, null, LOGOUT));

    verify(tokenCache).put(key, INACTIVE_TOKEN);
    verify(tokenCache).asMap();
  }

  @Test
  void invalidate_positive_logoutAllEventType() {
    var sessionId = "session1";
    var key = cacheKey(TENANT_NAME, USER_ID, sessionId);

    var cache = new ConcurrentHashMap<>(Map.of(key, activeTokenResponse()));
    when(tokenCache.asMap()).thenReturn(cache);

    introspectionService.invalidate(LogoutEvent.of(USER_ID, null, null, LOGOUT_ALL));

    verify(tokenCache).put(key, INACTIVE_TOKEN);
    verify(tokenCache).asMap();
  }

  @Test
  void checkActiveToken_positive_cachedToken() {
    var ctx = routingContext("tenant", "userId", "sessionId");
    var key = cacheKey("tenant", "userId", "sessionId");
    when(tokenCache.getIfPresent(key)).thenReturn(activeTokenResponse());

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.succeeded()).isTrue();
    verify(tokenCache).getIfPresent("tenant#userId#sessionId");
    verifyNoInteractions(keycloakClient);
  }

  @Test
  void checkActiveToken_positive_cachedTokenInactive() {
    var ctx = routingContext("tenant", "userId", "sessionId");
    var key = cacheKey("tenant", "userId", "sessionId");
    when(tokenCache.getIfPresent(key)).thenReturn(INACTIVE_TOKEN);

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.succeeded()).isFalse();
    verify(tokenCache).getIfPresent("tenant#userId#sessionId");
    verifyNoInteractions(keycloakClient);
  }

  @Test
  void checkActiveToken_positive_notCachedToken() {
    var originTenant = "tenant";
    var ctx = routingContext("tenant", "userId", "sessionId");
    var key = cacheKey(originTenant, "userId", "sessionId");
    when(tokenCache.getIfPresent(key)).thenReturn(null);
    var client = ClientCredentials.of("tenant-login", "secret");
    when(credentialService.getLoginClientCredentials(originTenant)).thenReturn(succeededFuture(client));
    when(keycloakClient.introspectToken(originTenant, client, JWT)).thenReturn(succeededFuture(introspectionResponse));
    when(introspectionResponse.statusCode()).thenReturn(200);
    when(introspectionResponse.bodyAsJson(TokenIntrospectionResponse.class)).thenReturn(activeTokenResponse());

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.succeeded()).isTrue();
    verify(tokenCache).getIfPresent(key);
    verify(keycloakClient).introspectToken("tenant", client, JWT);
  }

  @Test
  void checkActiveToken_positive_notCachedTokenInactive() {
    var originTenant = "tenant";
    var ctx = routingContext("tenant", "userId", "sessionId");
    var key = cacheKey(originTenant, "userId", "sessionId");
    when(tokenCache.getIfPresent(key)).thenReturn(null);
    var client = ClientCredentials.of("tenant-login", "secret");
    when(credentialService.getLoginClientCredentials(originTenant)).thenReturn(succeededFuture(client));
    when(keycloakClient.introspectToken(originTenant, client, JWT)).thenReturn(succeededFuture(introspectionResponse));
    when(introspectionResponse.statusCode()).thenReturn(200);
    when(introspectionResponse.bodyAsJson(TokenIntrospectionResponse.class)).thenReturn(INACTIVE_TOKEN);

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.succeeded()).isFalse();
    verify(tokenCache).getIfPresent(key);
    verify(keycloakClient).introspectToken("tenant", client, JWT);
  }

  @Test
  void checkActiveToken_negative_userIdClaimNotFound() {
    var ctx = routingContext("tenant", null, "sessionId");

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.failed()).isTrue();
  }

  @Test
  void checkActiveToken_negative_parsedTokenNotFound() {
    var ctx = routingContext("tenant", "userId", "sessionId");
    when(ctx.get(PARSED_TOKEN)).thenReturn(null);

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.failed()).isTrue();
  }

  private static String cacheKey(String tenant, String userId, String tokenSessionId) {
    return join("#", tenant, userId, tokenSessionId);
  }

  private static TokenIntrospectionResponse activeTokenResponse() {
    var token = new TokenIntrospectionResponse();
    token.setActive(true);
    return token;
  }

  private static RoutingContext routingContext(String originTenant, String userIdClaim, String sessionIdClaim) {
    var rc = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    var token = mock(JsonWebToken.class);
    when(rc.get(PARSED_TOKEN)).thenReturn(token);
    lenient().when(token.getClaim(USER_ID_CLAIM)).thenReturn(userIdClaim);
    lenient().when(rc.get(ORIGIN_TENANT)).thenReturn(originTenant);
    lenient().when(rc.request().getHeader(TOKEN)).thenReturn(JWT);
    lenient().when(token.getClaim(SESSION_ID_CLAIM)).thenReturn(sessionIdClaim);
    return rc;
  }
}
