package org.folio.sidecar.utils;

import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.eclipse.microprofile.config.ConfigProvider;

@UtilityClass
public class ConfigProviderUtils {

  private static final String NOT_FOUND_MESSAGE = "Failed to find required config property: ";

  public static String getValue(String prefix, String propertyName) {
    return getOptionalValue(prefix + propertyName, String.class).orElse(null);
  }

  public static String getRequiredValue(String prefix, String propertyName) {
    return getRequiredValue(prefix, propertyName, String.class);
  }

  public static <T> T getRequiredValue(String prefix, String propertyName, Class<T> type) {
    var property = prefix + propertyName;
    return getOptionalValue(property, type)
      .orElseThrow(() -> new NoSuchElementException(NOT_FOUND_MESSAGE + property));
  }

  public static <T> Optional<T> getOptionalValue(String property, Class<T> type) {
    return ConfigProvider.getConfig().getOptionalValue(property, type);
  }
}
