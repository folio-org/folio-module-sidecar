package org.folio.sidecar.utils;

import java.util.Map;
import java.util.Properties;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class FolioEnvironment {

  private static String secureStoreEnvName = getSecureStoreEnvName(System.getenv(), System.getProperties());

  /**
   * Return the first non-empty value: SECURE_STORE_ENV from env, secure.store.env from properties,
   * ENV from env, env from properties, "folio".
   */
  public static String getSecureStoreEnvName(Map<String, String> env, Properties properties) {
    return StringUtils.firstNonEmpty(
        env.get("SECURE_STORE_ENV"),
        properties.getProperty("secure.store.env"),
        env.get("ENV"),
        properties.getProperty("env"),
        "folio");
  }

  /**
   * Return the first non-empty value: SECURE_STORE_ENV environment variable, secure.store.env system property,
   * ENV environment variable, env system property, "folio".
   */
  public static String getSecureStoreEnvName() {
    return secureStoreEnvName;
  }
}
