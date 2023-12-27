package org.folio.sidecar.utils;

import static org.folio.sidecar.utils.FolioEnvironment.getFolioEnvName;
import static org.folio.sidecar.utils.StringUtils.isBlank;

import lombok.experimental.UtilityClass;

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

    return String.format("%s_%s_%s", getFolioEnvName(), tenant, key);
  }
}
