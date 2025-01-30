package org.folio.sidecar.utils;

import io.vertx.core.MultiMap;
import lombok.experimental.UtilityClass;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;

@UtilityClass
public class TokenRequestHelper {

  public static final String CLIENT_CREDENTIALS_GRANT_TYPE = "client_credentials";
  public static final String PASSWORD_GRANT_TYPE = "password";
  public static final String RPT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:uma-ticket";
  public static final String IMPERSONATION_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";

  private static final String GRANT_TYPE_FORM_FIELD = "grant_type";
  private static final String CLIENT_ID_FORM_FIELD = "client_id";
  private static final String CLIENT_SECRET_FORM_FIELD = "client_secret";
  private static final String USERNAME_FORM_FIELD = "username";
  private static final String PASSWORD_FORM_FIELD = "password";
  private static final String PERMISSION_FORM_FIELD = "permission";
  private static final String AUDIENCE_FORM_FIELD = "audience";
  private static final String REFRESH_TOKEN_FORM_FIELD = "refresh_token";
  private static final String REQUESTED_SUBJECT_FORM_FIELD = "requested_subject";
  private static final String TOKEN = "token";

  public static MultiMap preparePasswordRequestBody(ClientCredentials client, UserCredentials user) {
    return MultiMap.caseInsensitiveMultiMap()
      .set(GRANT_TYPE_FORM_FIELD, PASSWORD_GRANT_TYPE)
      .set(CLIENT_ID_FORM_FIELD, client.getClientId())
      .set(CLIENT_SECRET_FORM_FIELD, client.getClientSecret())
      .set(USERNAME_FORM_FIELD, user.getUsername())
      .set(PASSWORD_FORM_FIELD, user.getPassword());
  }

  public static MultiMap prepareClientRequestBody(ClientCredentials client) {
    return MultiMap.caseInsensitiveMultiMap()
      .set(GRANT_TYPE_FORM_FIELD, CLIENT_CREDENTIALS_GRANT_TYPE)
      .set(CLIENT_ID_FORM_FIELD, client.getClientId())
      .set(CLIENT_SECRET_FORM_FIELD, client.getClientSecret());
  }

  public static MultiMap prepareRptRequestBody(String clientId, String permission) {
    return MultiMap.caseInsensitiveMultiMap()
      .set(GRANT_TYPE_FORM_FIELD, RPT_GRANT_TYPE)
      .set(PERMISSION_FORM_FIELD, permission)
      .set(AUDIENCE_FORM_FIELD, clientId);
  }

  public static MultiMap prepareRefreshRequestBody(ClientCredentials client, String refreshToken) {
    return MultiMap.caseInsensitiveMultiMap()
      .set(GRANT_TYPE_FORM_FIELD, "refresh_token")
      .set(CLIENT_ID_FORM_FIELD, client.getClientId())
      .set(CLIENT_SECRET_FORM_FIELD, client.getClientSecret())
      .set(REFRESH_TOKEN_FORM_FIELD, refreshToken);
  }

  public static MultiMap prepareImpersonateRequestBody(ClientCredentials client, String username) {
    return MultiMap.caseInsensitiveMultiMap()
      .set(GRANT_TYPE_FORM_FIELD, IMPERSONATION_GRANT_TYPE)
      .set(CLIENT_ID_FORM_FIELD, client.getClientId())
      .set(CLIENT_SECRET_FORM_FIELD, client.getClientSecret())
      .set(REQUESTED_SUBJECT_FORM_FIELD, username);
  }

  public static MultiMap prepareIntrospectRequestBody(ClientCredentials client, String token) {
    return MultiMap.caseInsensitiveMultiMap()
      .set(GRANT_TYPE_FORM_FIELD, CLIENT_CREDENTIALS_GRANT_TYPE)
      .set(CLIENT_ID_FORM_FIELD, client.getClientId())
      .set(CLIENT_SECRET_FORM_FIELD, client.getClientSecret())
      .set("token_type_hint", "requesting_party_token")
      .set(TOKEN, token);
  }
}
