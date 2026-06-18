package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TokenRequestHelperTest {

  private static final UserCredentials TEST_USER = UserCredentials.of("test", "testpwd");

  @Test
  void preparePasswordRequestBody_positive() {
    var actual = TokenRequestHelper.preparePasswordRequestBody(TestConstants.LOGIN_CLIENT_CREDENTIALS, TEST_USER);

    assertThat(actual).hasSize(5);
    assertEquals(TokenRequestHelper.PASSWORD_GRANT_TYPE, actual.get("grant_type"));
    assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("client_id"));
    assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientSecret(), actual.get("client_secret"));
    assertEquals("test", actual.get("username"));
    assertEquals("testpwd", actual.get("password"));
  }

  @Test
  void prepareClientRequestBody_positive() {
    var actual = TokenRequestHelper.prepareClientRequestBody(TestConstants.LOGIN_CLIENT_CREDENTIALS);

    assertThat(actual).hasSize(3);
    assertEquals(TokenRequestHelper.CLIENT_CREDENTIALS_GRANT_TYPE, actual.get("grant_type"));
    assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("client_id"));
    assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientSecret(), actual.get("client_secret"));
  }

  @Test
  void prepareUmaDecisionRequestBody_positive() {
    var actual = TokenRequestHelper.prepareUmaDecisionRequestBody(
      TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(),
      "/foo/bar#GET"
    );

    assertThat(actual).hasSize(4);
    assertEquals(TokenRequestHelper.RPT_GRANT_TYPE, actual.get("grant_type"));
    assertEquals("/foo/bar#GET", actual.get("permission"));
    assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("audience"));
    assertEquals(TokenRequestHelper.DECISION_RESPONSE_MODE, actual.get("response_mode"));
  }

  @Test
  void prepareRefreshRequestBody_positive() {
    var actual = TokenRequestHelper.prepareRefreshRequestBody(TestConstants.LOGIN_CLIENT_CREDENTIALS, "refreshtoken");

    assertThat(actual).hasSize(4);
    assertEquals("refresh_token", actual.get("grant_type"));
    assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientId(), actual.get("client_id"));
    assertEquals(TestConstants.LOGIN_CLIENT_CREDENTIALS.getClientSecret(), actual.get("client_secret"));
    assertEquals("refreshtoken", actual.get("refresh_token"));
  }
}
