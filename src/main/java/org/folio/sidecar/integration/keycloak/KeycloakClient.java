package org.folio.sidecar.integration.keycloak;

import static org.folio.sidecar.utils.TokenRequestHelper.prepareClientRequestBody;
import static org.folio.sidecar.utils.TokenRequestHelper.prepareImpersonateRequestBody;
import static org.folio.sidecar.utils.TokenRequestHelper.prepareIntrospectRequestBody;
import static org.folio.sidecar.utils.TokenRequestHelper.preparePasswordRequestBody;
import static org.folio.sidecar.utils.TokenRequestHelper.prepareRefreshRequestBody;
import static org.folio.sidecar.utils.TokenRequestHelper.prepareRptRequestBody;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;

@Log4j2
@ApplicationScoped
public class KeycloakClient {

  private final WebClient webClient;
  private final KeycloakProperties properties;

  public KeycloakClient(@Named("webClientKeycloak") WebClient webClient, KeycloakProperties properties) {
    this.webClient = webClient;
    this.properties = properties;
  }

  public Future<HttpResponse<Buffer>> obtainToken(String realm, ClientCredentials client) {
    log.debug("Keycloak HTTP: POST token [realm={}, grant_type=client_credentials, client_id={}]",
      () -> realm, client::getClientId);
    var requestBody = prepareClientRequestBody(client);
    return webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody);
  }

  public Future<HttpResponse<Buffer>> obtainUserToken(String realm, ClientCredentials client, UserCredentials user) {
    log.debug("Keycloak HTTP: POST token [realm={}, grant_type=password, client_id={}, username={}]",
      () -> realm, client::getClientId, user::getUsername);
    var requestBody = preparePasswordRequestBody(client, user);
    return webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody);
  }

  public Future<HttpResponse<Buffer>> refreshUserToken(String realm, ClientCredentials client, String refreshToken) {
    log.debug("Keycloak HTTP: POST /realms/{}/protocol/openid-connect/token [grant_type=refresh_token, client_id={}]",
      () -> realm, client::getClientId);
    var requestBody = prepareRefreshRequestBody(client, refreshToken);
    return webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody);
  }

  public Future<HttpResponse<Buffer>> evaluatePermissions(String tenant, String permission, String accessToken) {
    log.debug("Keycloak HTTP: POST /realms/{}/protocol/openid-connect/token [grant_type=uma-ticket, permission={}]",
      () -> tenant, () -> permission);
    var clientId = tenant + properties.getLoginClientSuffix();
    var requestBody = prepareRptRequestBody(clientId, permission);
    return webClient.postAbs(resolveTokenUrl(tenant))
      .bearerTokenAuthentication(accessToken)
      .sendForm(requestBody);
  }

  public Future<HttpResponse<Buffer>> impersonateUserToken(String realm, ClientCredentials client, String username) {
    log.debug("Keycloak HTTP: POST token [realm={}, grant_type=token-exchange, client_id={}, username={}]",
      () -> realm, client::getClientId, () -> username);
    var requestBody = prepareImpersonateRequestBody(client, username);
    return webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody);
  }

  public Future<HttpResponse<Buffer>> introspectToken(String realm, ClientCredentials client, String token) {
    log.debug("Keycloak HTTP: POST /realms/{}/protocol/openid-connect/token/introspect [client_id={}]",
      () -> realm, client::getClientId);
    var requestBody = prepareIntrospectRequestBody(client, token);
    var url = String.format("%s/realms/%s/protocol/openid-connect/token/introspect", properties.getUrl(), realm);
    return webClient.postAbs(url).sendForm(requestBody);
  }

  private String resolveTokenUrl(String realm) {
    return String.format("%s/realms/%s/protocol/openid-connect/token", properties.getUrl(), realm);
  }
}
