package org.folio.sidecar.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConfigProviderUtilsTest {

  @Mock private Config config;

  @Test
  void getRequiredProperty_positive() {
    try (MockedStatic<ConfigProvider> configProvider = mockStatic(ConfigProvider.class)) {
      configProvider.when(ConfigProvider::getConfig).thenReturn(config);

      when(config.getOptionalValue("test.foo", String.class)).thenReturn(Optional.of("value"));

      var value = ConfigProviderUtils.getRequiredValue("test.", "foo");

      assertEquals("value", value);
    }
  }

  @Test
  void getRequiredProperty_negative_notFound() {
    try (MockedStatic<ConfigProvider> configProvider = mockStatic(ConfigProvider.class)) {
      configProvider.when(ConfigProvider::getConfig).thenReturn(config);
      when(config.getOptionalValue("test.foo", String.class)).thenReturn(Optional.empty());

      var error =
        assertThrows(NoSuchElementException.class, () -> ConfigProviderUtils.getRequiredValue("test.", "foo"));

      assertEquals("Failed to find required config property: test.foo", error.getMessage());
    }
  }

  @Test
  void getRequiredBooleanProperty_positive() {
    try (MockedStatic<ConfigProvider> configProvider = mockStatic(ConfigProvider.class)) {
      configProvider.when(ConfigProvider::getConfig).thenReturn(config);

      when(config.getOptionalValue("test.foo", Boolean.class)).thenReturn(Optional.of(Boolean.TRUE));

      var value = ConfigProviderUtils.getRequiredValue("test.", "foo", Boolean.class);

      assertEquals(Boolean.TRUE, value);
    }
  }

  @Test
  void getProperty_positive() {
    try (MockedStatic<ConfigProvider> configProvider = mockStatic(ConfigProvider.class)) {
      configProvider.when(ConfigProvider::getConfig).thenReturn(config);

      when(config.getOptionalValue("test.foo", String.class)).thenReturn(Optional.of("value"));

      var value = ConfigProviderUtils.getValue("test.", "foo");

      assertEquals("value", value);
    }
  }

  @Test
  void getProperty_negative_notFound() {
    try (MockedStatic<ConfigProvider> configProvider = mockStatic(ConfigProvider.class)) {
      configProvider.when(ConfigProvider::getConfig).thenReturn(config);

      when(config.getOptionalValue("test.foo", String.class)).thenReturn(Optional.empty());

      var value = ConfigProviderUtils.getValue("test.", "foo");

      assertNull(value);
    }
  }
}
