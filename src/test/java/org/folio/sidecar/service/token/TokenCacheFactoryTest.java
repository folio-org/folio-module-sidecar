package org.folio.sidecar.service.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.function.BiConsumer;
import org.folio.sidecar.configuration.properties.TokenCacheProperties;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TokenCacheFactoryTest {

  @Mock private TokenCacheProperties cacheProperties;
  @InjectMocks private TokenCacheFactory factory;

  @Test
  void createCache_positive() {
    when(cacheProperties.getInitialCapacity()).thenReturn(10);
    when(cacheProperties.getMaxCapacity()).thenReturn(50);

    var actual = factory.createCache(refreshFunction());
    assertThat(actual).isNotNull();
  }

  @Test
  void createCache_positive_withoutRefresh() {
    when(cacheProperties.getInitialCapacity()).thenReturn(10);
    when(cacheProperties.getMaxCapacity()).thenReturn(50);

    var actual = factory.createCache();
    assertThat(actual).isNotNull();
  }

  @Test
  void constructor_validation_negative_nullObject() {
    assertThatThrownBy(() -> new TokenCacheFactory(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("Token cache properties must be provided");
  }

  @Test
  void constructor_validation_negative_nullInitialCapacity() {
    var cacheProperties = new TokenCacheProperties();
    cacheProperties.setMaxCapacity(10);
    cacheProperties.setRefreshBeforeExpirySeconds(10);
    assertThatThrownBy(() -> new TokenCacheFactory(cacheProperties))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("Token cache initial capacity must be set");
  }

  @Test
  void constructor_validation_negative_nullMaxCapacity() {
    var cacheProperties = new TokenCacheProperties();
    cacheProperties.setInitialCapacity(10);
    cacheProperties.setRefreshBeforeExpirySeconds(10);
    assertThatThrownBy(() -> new TokenCacheFactory(cacheProperties))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("Token cache max capacity must be set");
  }

  @Test
  void constructor_validation_negative_nullRefreshBeforeExpirySeconds() {
    var cacheProperties = new TokenCacheProperties();
    cacheProperties.setInitialCapacity(10);
    cacheProperties.setMaxCapacity(10);
    assertThatThrownBy(() -> new TokenCacheFactory(cacheProperties))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("Token cache refresh before expiry must be set");
  }

  private static BiConsumer<String, TokenResponse> refreshFunction() {
    return (tenant, token) -> {};
  }
}
