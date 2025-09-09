package org.folio.sidecar.utils;

import static org.apache.commons.lang3.StringUtils.isBlank;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class SecureStoreUtils {

  public static final String GLOBAL_SECTION = "master";

  public static String globalStoreKey(String key) {
    return tenantStoreKey(GLOBAL_SECTION, key);
  }

  public static String tenantStoreKey(String tenant, String key) {
    if (isBlank(tenant)) {
      throw new IllegalArgumentException("Tenant cannot be empty");
    }
    if (isBlank(key)) {
      throw new IllegalArgumentException("Client id cannot be empty");
    }

    return String.format("%s_%s_%s", getSecureStoreEnvName(), tenant, key);
  }

  /**
   * Return the first non-empty value: SECURE_STORE_ENV from env, secure.store.env from properties,
   * ENV from env, env from properties, "folio".
   */
  public static String getSecureStoreEnvName() {
    return StringUtils.firstNonEmpty(
      System.getenv("SECURE_STORE_ENV"),
      System.getProperty("secure.store.env"),
      System.getenv("ENV"),
      System.getProperty("env"),
      "folio");
  }
}
