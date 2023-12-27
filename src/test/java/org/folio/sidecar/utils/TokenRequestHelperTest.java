package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.sidecar.model.UserCredentials;
import org.folio.sidecar.support.TestConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TokenRequestHelperTest {

  private static final UserCredentials TEST_USER = UserCredentials.of("test", "testpwd");

  @Test
  void preparePasswordRequestBody_positive() {
    var actual = TokenRequestHelper.preparePasswordRequestBody(TestConstants.LOGIN_CLIENT_CREDENTIALS, TEST_USER);

    assertThat(actual).hasSize(5);
    Assertions.assertEquals(TokenRequestHelper.PASSWORD_GRANT_TYPE, actual.get("grant_type"));
    Assertions.assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("client_id"));
    Assertions.assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientSecret(), actual.get("client_secret"));
    assertEquals("test", actual.get("username"));
    assertEquals("testpwd", actual.get("password"));
  }

  @Test
  void prepareClientRequestBody_positive() {
    var actual = TokenRequestHelper.prepareClientRequestBody(TestConstants.LOGIN_CLIENT_CREDENTIALS);

    assertThat(actual).hasSize(3);
    Assertions.assertEquals(TokenRequestHelper.CLIENT_CREDENTIALS_GRANT_TYPE, actual.get("grant_type"));
    Assertions.assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("client_id"));
    Assertions.assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientSecret(), actual.get("client_secret"));
  }

  @Test
  void prepareRptRequestBody_positive() {
    var actual =
      TokenRequestHelper.prepareRptRequestBody(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), "/foo/bar#GET");

    assertThat(actual).hasSize(3);
    Assertions.assertEquals(TokenRequestHelper.RPT_GRANT_TYPE, actual.get("grant_type"));
    assertEquals("/foo/bar#GET", actual.get("permission"));
    Assertions.assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("audience"));
  }

  @Test
  void prepareRefreshRequestBody_positive() {
    var actual = TokenRequestHelper.prepareRefreshRequestBody(TestConstants.LOGIN_CLIENT_CREDENTIALS, "refreshtoken");

    assertThat(actual).hasSize(4);
    assertEquals("refresh_token", actual.get("grant_type"));
    Assertions.assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("client_id"));
    Assertions.assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientSecret(), actual.get("client_secret"));
    assertEquals("refreshtoken", actual.get("refresh_token"));
  }
}
