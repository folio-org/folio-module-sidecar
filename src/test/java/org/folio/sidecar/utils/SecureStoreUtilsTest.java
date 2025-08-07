package org.folio.sidecar.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedStatic;

@UnitTest
class SecureStoreUtilsTest {

  private static final String TEST_ENV = "test";
  private static final String TEST_CLIENT_ID = "test-client-id";
  private static final String TEST_TENANT = "test-tenant";

  @Test
  void globalStoreKey_positive() {
    try (MockedStatic<FolioEnvironment> mockedEnv = mockStatic(FolioEnvironment.class)) {
      mockedEnv.when(FolioEnvironment::getSecureStoreEnvName).thenReturn(TEST_ENV);

      var key = SecureStoreUtils.globalStoreKey(TEST_CLIENT_ID);

      assertEquals(key(SecureStoreUtils.GLOBAL_SECTION, TEST_CLIENT_ID), key);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  void globalStoreKey_negative_clientIdEmpty(String clientId) {
    var exc = assertThrows(IllegalArgumentException.class, () -> SecureStoreUtils.globalStoreKey(clientId));

    assertEquals("Client id cannot be empty", exc.getMessage());
  }

  @Test
  void tenantStoreKey_positive() {
    try (MockedStatic<FolioEnvironment> mockedEnv = mockStatic(FolioEnvironment.class)) {
      mockedEnv.when(FolioEnvironment::getSecureStoreEnvName).thenReturn(TEST_ENV);

      var key = SecureStoreUtils.tenantStoreKey(TEST_TENANT, TEST_CLIENT_ID);

      assertEquals(key(TEST_TENANT, TEST_CLIENT_ID), key);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  void tenantStoreKey_negative_clientIdEmpty(String clientId) {
    var exc =
      assertThrows(IllegalArgumentException.class, () -> SecureStoreUtils.tenantStoreKey(TEST_TENANT, clientId));

    assertEquals("Client id cannot be empty", exc.getMessage());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void tenantStoreKey_negative_tenantEmpty(String tenant) {
    var exc =
      assertThrows(IllegalArgumentException.class, () -> SecureStoreUtils.tenantStoreKey(tenant, TEST_CLIENT_ID));

    assertEquals("Tenant cannot be empty", exc.getMessage());
  }

  private static String key(String tenant, String client) {
    return String.format("%s_%s_%s", TEST_ENV, tenant, client);
  }
}
