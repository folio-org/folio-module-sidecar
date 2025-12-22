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
import io.netty.handler.codec.http.HttpResponseStatus;
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
  private static final Long EXPIRATION_TIME = 1700000000L;

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
  void checkActiveToken_positive_differentExpirationTimesHaveDifferentCacheKeys() {
    var firstExpiration = 1700000000L;
    var secondExpiration = 1700001000L;
    var ctx1 = routingContextWithExpiration("tenant", "userId", "sessionId", firstExpiration);
    var ctx2 = routingContextWithExpiration("tenant", "userId", "sessionId", secondExpiration);

    var key1 = join("#", "tenant", "userId", "sessionId", String.valueOf(firstExpiration));
    var key2 = join("#", "tenant", "userId", "sessionId", String.valueOf(secondExpiration));

    // Cache hit for first token
    when(tokenCache.getIfPresent(key1)).thenReturn(activeTokenResponse());

    var result1 = introspectionService.checkActiveToken(ctx1);
    assertThat(result1.succeeded()).isTrue();
    verify(tokenCache).getIfPresent(key1);

    // Cache miss for second token (different expiration time = different key)
    when(tokenCache.getIfPresent(key2)).thenReturn(null);
    var client = ClientCredentials.of("tenant-login", "secret");
    when(credentialService.getLoginClientCredentials("tenant")).thenReturn(succeededFuture(client));
    when(keycloakClient.introspectToken("tenant", client, JWT)).thenReturn(succeededFuture(introspectionResponse));
    when(introspectionResponse.statusCode()).thenReturn(200);
    when(introspectionResponse.bodyAsJson(TokenIntrospectionResponse.class)).thenReturn(activeTokenResponse());

    var result2 = introspectionService.checkActiveToken(ctx2);
    assertThat(result2.succeeded()).isTrue();
    verify(tokenCache).getIfPresent(key2);
    // Verifies that Keycloak was actually called for the second token
    verify(keycloakClient).introspectToken("tenant", client, JWT);
  }

  @Test
  void checkActiveToken_positive_cachedToken() {
    var ctx = routingContext("tenant", "userId", "sessionId");
    var key = cacheKey("tenant", "userId", "sessionId");
    when(tokenCache.getIfPresent(key)).thenReturn(activeTokenResponse());

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.succeeded()).isTrue();
    verify(tokenCache).getIfPresent(key);
    verifyNoInteractions(keycloakClient);
  }

  @Test
  void checkActiveToken_positive_cachedTokenInactive() {
    var ctx = routingContext("tenant", "userId", "sessionId");
    var key = cacheKey("tenant", "userId", "sessionId");
    when(tokenCache.getIfPresent(key)).thenReturn(INACTIVE_TOKEN);

    var routingContextFuture = introspectionService.checkActiveToken(ctx);

    assertThat(routingContextFuture.succeeded()).isFalse();
    verify(tokenCache).getIfPresent(key);
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
  void checkActiveToken_positive_recoveredFromOutdatedLoginCredentials() {
    var originTenant = "tenant";
    var ctx = routingContext("tenant", "userId", "sessionId");
    var key = cacheKey(originTenant, "userId", "sessionId");
    when(tokenCache.getIfPresent(key)).thenReturn(null);

    var outdatedCreds = ClientCredentials.of("tenant-login", "secret-old");
    var credentials = ClientCredentials.of("tenant-login", "secret");
    when(credentialService.getLoginClientCredentials(originTenant))
      .thenReturn(succeededFuture(outdatedCreds))
      .thenReturn(succeededFuture(credentials));

    var unauthorized = (HttpResponse<Buffer>) mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(HttpResponseStatus.UNAUTHORIZED.code());
    when(unauthorized.bodyAsString()).thenReturn("Unauthorized");
    when(introspectionResponse.statusCode()).thenReturn(200);
    when(introspectionResponse.bodyAsJson(TokenIntrospectionResponse.class)).thenReturn(activeTokenResponse());

    when(keycloakClient.introspectToken(originTenant, outdatedCreds, JWT)).thenReturn(succeededFuture(unauthorized));
    when(keycloakClient.introspectToken(originTenant, credentials, JWT))
      .thenReturn(succeededFuture(introspectionResponse));

    var result = introspectionService.checkActiveToken(ctx);

    assertThat(result.succeeded()).isTrue();
    verify(credentialService).resetLoginClientCredentials(originTenant);
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
    return join("#", tenant, userId, tokenSessionId, String.valueOf(EXPIRATION_TIME));
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
    lenient().when(token.getExpirationTime()).thenReturn(EXPIRATION_TIME);
    return rc;
  }

  private static RoutingContext routingContextWithExpiration(String originTenant, String userIdClaim,
    String sessionIdClaim, Long expirationTime) {
    var rc = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    var token = mock(JsonWebToken.class);
    when(rc.get(PARSED_TOKEN)).thenReturn(token);
    lenient().when(token.getClaim(USER_ID_CLAIM)).thenReturn(userIdClaim);
    lenient().when(rc.get(ORIGIN_TENANT)).thenReturn(originTenant);
    lenient().when(rc.request().getHeader(TOKEN)).thenReturn(JWT);
    lenient().when(token.getClaim(SESSION_ID_CLAIM)).thenReturn(sessionIdClaim);
    lenient().when(token.getExpirationTime()).thenReturn(expirationTime);
    return rc;
  }
}
