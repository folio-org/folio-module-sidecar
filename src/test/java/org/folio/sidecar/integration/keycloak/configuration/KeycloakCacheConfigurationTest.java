package org.folio.sidecar.integration.keycloak.configuration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakCacheConfigurationTest {

  @InjectMocks private KeycloakCacheConfiguration keycloakCacheConfiguration;
  @Mock private KeycloakProperties keycloakProperties;

  @Test
  void cacheExpirationCheck() {
    when(keycloakProperties.getAuthorizationCacheMaxSize()).thenReturn(50L);
    when(keycloakProperties.getAuthorizationCacheTtlOffset()).thenReturn(1000L);
    var jsonWebTokenCacheExpiry = new JsonWebTokenExpiry(keycloakProperties);

    var jsonWebToken = mock(JsonWebToken.class);
    when(jsonWebToken.getExpirationTime()).thenReturn(MILLISECONDS.toSeconds(System.currentTimeMillis() + 2000));

    var cache = keycloakCacheConfiguration.kcAuthorizationCache(keycloakProperties, jsonWebTokenCacheExpiry);

    var cacheKey = "test-key";
    cache.put(cacheKey, jsonWebToken);

    await()
      .atMost(Duration.ofMillis(1500))
      .pollInterval(Duration.ofMillis(50))
      .untilAsserted(() -> assertThat(cache.getIfPresent(cacheKey)).isNull());
  }
}
