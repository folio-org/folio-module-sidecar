package org.folio.sidecar.integration.keycloak.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.jwt.openid.JsonWebTokenParser.INVALID_SEGMENTS_JWT_ERROR_MSG;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.AUTHORIZATION;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.SYSTEM_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.USER_ID;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.utils.JwtUtils.USER_ID_CLAIM;
import static org.folio.sidecar.utils.RoutingUtils.ORIGIN_TENANT;
import static org.folio.sidecar.utils.RoutingUtils.PARSED_TOKEN;
import static org.folio.sidecar.utils.RoutingUtils.SC_ROUTING_ENTRY_KEY;
import static org.folio.sidecar.utils.RoutingUtils.SELF_REQUEST_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.jwt.openid.JsonWebTokenParser;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakJwtFilterTest extends AbstractFilterTest {

  private static final String TEST_USER_ID = UUID.randomUUID().toString();

  @Mock private JsonWebTokenParser jsonWebTokenParser;
  @Mock private Cache<String, JsonWebToken> parsedTokenCache;
  @Mock private HttpServerRequest request;

  @InjectMocks private KeycloakJwtFilter keycloakJwtFilter;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(jsonWebTokenParser);
  }

  @Test
  void filter_positive() throws Exception {
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var jsonWebToken = mock(JsonWebToken.class);
    when(jsonWebTokenParser.parse(AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getClaim(USER_ID_CLAIM)).thenReturn(TEST_USER_ID);
    when(jsonWebToken.getIssuer()).thenReturn("http://localhost:8080/auth/realms/" + TENANT_NAME);

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verify(routingContext).put(PARSED_TOKEN, jsonWebToken);
    verify(routingContext).put(ORIGIN_TENANT, TENANT_NAME);
    verify(parsedTokenCache).put(AUTH_TOKEN, jsonWebToken);

    assertThat(requestHeaders.get(TOKEN)).isEqualTo(AUTH_TOKEN);
    assertThat(requestHeaders.get(USER_ID)).isEqualTo(TEST_USER_ID);
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
  }

  @Test
  void filter_positive_cachedToken() {
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var jsonWebToken = mock(JsonWebToken.class);
    when(parsedTokenCache.getIfPresent(AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getClaim(USER_ID_CLAIM)).thenReturn(TEST_USER_ID);
    when(jsonWebToken.getIssuer()).thenReturn("http://localhost:8080/auth/realms/" + TENANT_NAME);

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verify(routingContext).put(PARSED_TOKEN, jsonWebToken);
    verify(routingContext).put(ORIGIN_TENANT, TENANT_NAME);
    verifyNoInteractions(jsonWebTokenParser);

    assertThat(requestHeaders.get(TOKEN)).isEqualTo(AUTH_TOKEN);
    assertThat(requestHeaders.get(USER_ID)).isEqualTo(TEST_USER_ID);
  }

  @Test
  void filter_positive_userIdFromHeader() throws Exception {
    var customUserId = UUID.randomUUID().toString();
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN, USER_ID, customUserId));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var jsonWebToken = mock(JsonWebToken.class);
    when(jsonWebTokenParser.parse(AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getIssuer()).thenReturn("http://localhost:8080/auth/realms/" + TENANT_NAME);

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verify(routingContext).put(PARSED_TOKEN, jsonWebToken);
    verify(routingContext).put(ORIGIN_TENANT, TENANT_NAME);

    assertThat(requestHeaders.get(TOKEN)).isEqualTo(AUTH_TOKEN);
    assertThat(requestHeaders.get(USER_ID)).isEqualTo(customUserId);
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
    verifyNoMoreInteractions(jsonWebToken);
  }

  @Test
  void filter_positive_authorizationHeader() throws Exception {
    var requestHeaders = headers(Map.of(AUTHORIZATION, "Bearer " + AUTH_TOKEN));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var jsonWebToken = mock(JsonWebToken.class);
    when(jsonWebTokenParser.parse(AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getClaim(USER_ID_CLAIM)).thenReturn(TEST_USER_ID);
    when(jsonWebToken.getIssuer()).thenReturn("http://localhost:8080/auth/realms/" + TENANT_NAME);

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verify(routingContext).put(PARSED_TOKEN, jsonWebToken);
    verify(routingContext).put(ORIGIN_TENANT, TENANT_NAME);

    assertThat(requestHeaders.get(TOKEN)).isEqualTo(AUTH_TOKEN);
    assertThat(requestHeaders.get(USER_ID)).isEqualTo(TEST_USER_ID);
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
  }

  @Test
  void filter_positive_accessTokenAndSystemAccessToken() throws Exception {
    var systemToken = "c3lzdGVtLWFjY2Vzcy10b2tlbg==";
    var systemJwt = mock(JsonWebToken.class);
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN, SYSTEM_TOKEN, systemToken));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemJwt);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var jsonWebToken = mock(JsonWebToken.class);
    when(jsonWebTokenParser.parse(AUTH_TOKEN)).thenReturn(jsonWebToken);
    when(jsonWebToken.getClaim(USER_ID_CLAIM)).thenReturn(TEST_USER_ID);
    when(jsonWebToken.getIssuer()).thenReturn("http://localhost:8080/auth/realms/" + TENANT_NAME);

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verify(routingContext).put(PARSED_TOKEN, jsonWebToken);
    verify(routingContext).put(ORIGIN_TENANT, TENANT_NAME);

    assertThat(requestHeaders.get(TOKEN)).isEqualTo(AUTH_TOKEN);
    assertThat(requestHeaders.get(SYSTEM_TOKEN)).isEqualTo(systemToken);
    assertThat(requestHeaders.get(USER_ID)).isEqualTo(TEST_USER_ID);
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
  }

  @Test
  void filter_positive_systemTokenAndInvalidAmountOfSegmentsInJwt() throws Exception {
    var systemToken = "c3lzdGVtLWFjY2Vzcy10b2tlbg==";
    var systemJwt = mock(JsonWebToken.class);
    var dummyToken = "Dummy";
    var requestHeaders = headers(Map.of(TOKEN, dummyToken, SYSTEM_TOKEN, systemToken));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemJwt);
      when(request.headers()).thenReturn(requestHeaders);
    });

    when(jsonWebTokenParser.parse(dummyToken)).thenThrow(new ParseException(INVALID_SEGMENTS_JWT_ERROR_MSG));

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verify(routingContext, never()).put(anyString(), any());

    assertThat(requestHeaders.get(TOKEN)).isEqualTo(dummyToken);
    assertThat(requestHeaders.get(SYSTEM_TOKEN)).isEqualTo(systemToken);
    assertThat(requestHeaders.get(USER_ID)).isNull();
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
  }

  @Test
  void filter_positive_systemTokenAndMissingAccessToken() {
    var systemToken = "c3lzdGVtLWFjY2Vzcy10b2tlbg==";
    var systemJwt = mock(JsonWebToken.class);
    var requestHeaders = headers(Map.of(SYSTEM_TOKEN, systemToken));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemJwt);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
    verify(routingContext, never()).put(anyString(), any());

    assertThat(requestHeaders.get(SYSTEM_TOKEN)).isEqualTo(systemToken);
    verifyNoInteractions(jsonWebTokenParser);
  }

  @Test
  void filter_negative_systemTokenAndUnknownErrorForJwt() throws Exception {
    var systemToken = "c3lzdGVtLWFjY2Vzcy10b2tlbg==";
    var systemJwt = mock(JsonWebToken.class);
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN, SYSTEM_TOKEN, systemToken));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemJwt);
      when(request.headers()).thenReturn(requestHeaders);
      when(rc.get(SELF_REQUEST_KEY)).thenReturn(false);
    });

    when(jsonWebTokenParser.parse(AUTH_TOKEN)).thenThrow(new ParseException("Invalid JWT"));

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(requestHeaders.get(TOKEN)).isEqualTo(AUTH_TOKEN);
    assertThat(requestHeaders.get(SYSTEM_TOKEN)).isEqualTo(systemToken);
    assertThat(requestHeaders.get(USER_ID)).isNull();
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
  }

  @Test
  void filter_positive_missingAuthenticationInformation() {
    var requestHeaders = headers(Collections.emptyMap());
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Failed to find JWT in request");
  }

  @Test
  void filter_negative_tokenMismatch() {
    var token1 = "dG9rZW4tb25l";
    var token2 = "dG9rZW4tdHdv";
    var requestHeaders = headers(Map.of(AUTHORIZATION, "Bearer " + token1, TOKEN, token2));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    assertThatThrownBy(() -> keycloakJwtFilter.applyFilter(routingContext))
      .isInstanceOf(BadRequestException.class)
      .hasMessage("X-Okapi-Token is not equal to Authorization token");

    verifyNoInteractions(jsonWebTokenParser);
  }

  @Test
  void filter_negative_parsingFailure() throws Exception {
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    when(jsonWebTokenParser.parse(AUTH_TOKEN)).thenThrow(new ParseException("Failed to parse JWT, invalid offset"));

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Failed to parse JWT");
  }

  @Test
  void filter_positive_selfRequestWithoutToken() {
    var requestHeaders = headers(Collections.emptyMap());
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
      when(rc.get(SELF_REQUEST_KEY)).thenReturn(true);
    });

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);

    assertThat(requestHeaders.get(TOKEN)).isNull();
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
  }

  @Test
  void filter_negative_selfRequestWithInvalidToken() throws ParseException {
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN));
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
      when(rc.get(SELF_REQUEST_KEY)).thenReturn(true);
    });

    when(jsonWebTokenParser.parse(AUTH_TOKEN)).thenThrow(new ParseException("Invalid JWT"));

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Failed to parse JWT");

    verify(routingContext, never()).put(anyString(), any());
    assertThat(requestHeaders.get(TOKEN)).isEqualTo(AUTH_TOKEN);
    assertThat(requestHeaders.get(USER_ID)).isNull();
    assertThat(requestHeaders.get(AUTHORIZATION)).isNull();
  }

  @Test
  void getOrder_positive() {
    var actual = keycloakJwtFilter.getOrder();
    assertThat(actual).isEqualTo(120);
  }

  @Test
  void shouldSkip_positive() {
    var routingContext = routingContext(scRoutingEntry(), rc -> {});
    var actual = keycloakJwtFilter.shouldSkip(routingContext);
    assertThat(actual).isFalse();
  }

  @Test
  void shouldSkip_positive_systemRequest() {
    var routingContext = routingContext(scRoutingEntry("system", "foo.item.get"), rc -> {});
    var actual = keycloakJwtFilter.shouldSkip(routingContext);
    assertThat(actual).isTrue();
  }

  @Test
  void shouldSkip_negative_noRequiredPermissions() {
    var routingContext = routingContext(scRoutingEntry("not-system"), rc -> {});
    var actual = keycloakJwtFilter.shouldSkip(routingContext);
    assertThat(actual).isFalse();
  }

  @Test
  void shouldSkip_negative_timerEndpoint() {
    var routingContext = routingContext(scRoutingEntryWithId("system", "_timer"), rc -> {});
    var actual = keycloakJwtFilter.shouldSkip(routingContext);
    assertThat(actual).isFalse();
  }

  private static RoutingContext routingContext(ScRoutingEntry routingEntry, Consumer<RoutingContext> rcModifier) {
    var routingContext = mock(RoutingContext.class);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(routingEntry);
    rcModifier.accept(routingContext);
    return routingContext;
  }

  private static MultiMap headers(Map<String, String> headers) {
    var entries = new HeadersMultiMap();
    entries.addAll(headers);
    return entries;
  }
}
