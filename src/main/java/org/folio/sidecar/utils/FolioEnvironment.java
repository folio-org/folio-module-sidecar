package org.folio.sidecar.utils;

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static org.folio.sidecar.utils.StringUtils.isEmpty;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FolioEnvironment {

  /**
   * Return folio env name from environment or system properties as {@link String} object.
   *
   * @return folio env name.
   */
  public static String getFolioEnvName() {
    var env = getenv("ENV");
    if (!isEmpty(env)) {
      return env;
    } else {
      var propertyEnv = getProperty("env");
      if (!isEmpty(propertyEnv)) {
        return propertyEnv;
      }
    }
    return "folio";
  }
}
