package org.folio.sidecar.utils;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SemverUtils {

  private static final Pattern VERSION_PATTERN = Pattern.compile(
    "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
      + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" //NOSONAR
      + "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" //NOSONAR
      + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

  public static boolean hasVersion(String sourceId) {
    return VERSION_PATTERN.matcher(sourceId).matches();
  }
}
