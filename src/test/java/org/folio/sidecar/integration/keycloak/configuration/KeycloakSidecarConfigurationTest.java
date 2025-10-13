package org.folio.sidecar.integration.keycloak.configuration;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.jwt.openid.OpenidJwtParserProvider;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakSidecarConfigurationTest {

  @InjectMocks private KeycloakSidecarConfiguration keycloakSidecarConfiguration;
  @Mock private KeycloakProperties keycloakProperties;

  @Test
  void cacheExpirationCheck_positive_tokenIsExpired() {
    when(keycloakProperties.getAuthorizationCacheMaxSize()).thenReturn(50L);
    when(keycloakProperties.getAuthorizationCacheTtlOffset()).thenReturn(500L); //millis
    var jsonWebTokenCacheExpiry = new JsonWebTokenExpiry(keycloakProperties);

    var jsonWebToken = mock(JsonWebToken.class);
    when(jsonWebToken.getExpirationTime()).thenReturn(MILLISECONDS.toSeconds(currentTimeMillis() + 4000));

    var cache = keycloakSidecarConfiguration.kcAuthorizationCache(keycloakProperties, jsonWebTokenCacheExpiry);

    var cacheKey = "test-key";
    cache.put(cacheKey, jsonWebToken);

    await()
      .atMost(Duration.ofMillis(5000))
      .pollDelay(Duration.ofMillis(100))
      .pollInterval(Duration.ofMillis(200))
      .untilAsserted(() -> assertThat(cache.getIfPresent(cacheKey)).isNull());
  }

  @Test
  void cacheExpirationCheck_positive_tokenIsPresented() {
    when(keycloakProperties.getAuthorizationCacheMaxSize()).thenReturn(50L);
    when(keycloakProperties.getAuthorizationCacheTtlOffset()).thenReturn(500L); //millis
    var jsonWebTokenCacheExpiry = new JsonWebTokenExpiry(keycloakProperties);

    var jsonWebToken = mock(JsonWebToken.class);
    when(jsonWebToken.getExpirationTime()).thenReturn(MILLISECONDS.toSeconds(currentTimeMillis() + 4000));

    var cache = keycloakSidecarConfiguration.kcAuthorizationCache(keycloakProperties, jsonWebTokenCacheExpiry);

    var cacheKey = "test-key";
    cache.put(cacheKey, jsonWebToken);

    await()
      .atMost(Duration.ofMillis(5000))
      .pollDelay(Duration.ofMillis(1000))
      .pollInterval(Duration.ofMillis(200))
      .untilAsserted(() -> assertThat(cache.getIfPresent(cacheKey)).isNotNull());
  }

  @Test
  void openidJwtParserProvider_positive_withJwksBaseUrl() {
    when(keycloakProperties.getJwksRefreshInterval()).thenReturn(60);
    when(keycloakProperties.getForcedJwksRefreshInterval()).thenReturn(300);
    when(keycloakProperties.getJwksBaseUrl()).thenReturn("https://custom-keycloak.example.com");

    var result = keycloakSidecarConfiguration.openidJwtParserProvider(keycloakProperties);

    assertThat(result).isNotNull().isInstanceOf(OpenidJwtParserProvider.class);
  }

  @Test
  void jsonWebTokenParser_positive() {
    when(keycloakProperties.getUrl()).thenReturn("https://keycloak.example.com");
    when(keycloakProperties.isUriValidationEnabled()).thenReturn(true);

    var openidJwtParserProvider = mock(OpenidJwtParserProvider.class);
    var objectMapper = mock(ObjectMapper.class);

    var result = keycloakSidecarConfiguration.jsonWebTokenParser(
      keycloakProperties, openidJwtParserProvider, objectMapper);

    assertThat(result).isNotNull();
  }
}
