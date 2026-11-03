package org.folio.sidecar.integration.keycloak;

import static org.folio.sidecar.utils.OutboundRequestLog.trace;
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
    return obtainToken(realm, client, null);
  }

  public Future<HttpResponse<Buffer>> obtainToken(String realm, ClientCredentials client, String parentId) {
    var requestBody = prepareClientRequestBody(client);
    return trace("keycloak.client-token", "POST", resolveTokenUrl(realm), realm, parentId,
      () -> webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody));
  }

  public Future<HttpResponse<Buffer>> obtainUserToken(String realm, ClientCredentials client, UserCredentials user) {
    var requestBody = preparePasswordRequestBody(client, user);
    return trace("keycloak.password-token", "POST", resolveTokenUrl(realm), realm, null,
      () -> webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody));
  }

  public Future<HttpResponse<Buffer>> refreshUserToken(String realm, ClientCredentials client, String refreshToken) {
    var requestBody = prepareRefreshRequestBody(client, refreshToken);
    return trace("keycloak.refresh-token", "POST", resolveTokenUrl(realm), realm, null,
      () -> webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody));
  }

  public Future<HttpResponse<Buffer>> evaluatePermissions(String tenant, String permission, String accessToken) {
    return evaluatePermissions(tenant, permission, accessToken, null);
  }

  public Future<HttpResponse<Buffer>> evaluatePermissions(String tenant, String permission, String accessToken,
    String parentId) {
    var clientId = tenant + properties.getLoginClientSuffix();
    var requestBody = prepareRptRequestBody(clientId, permission);
    return trace("keycloak.authorization", "POST", resolveTokenUrl(tenant), tenant, parentId,
      () -> webClient.postAbs(resolveTokenUrl(tenant))
      .bearerTokenAuthentication(accessToken)
      .sendForm(requestBody));
  }

  public Future<HttpResponse<Buffer>> impersonateUserToken(String realm, ClientCredentials client, String username) {
    var requestBody = prepareImpersonateRequestBody(client, username);
    return trace("keycloak.impersonation", "POST", resolveTokenUrl(realm), realm, null,
      () -> webClient.postAbs(resolveTokenUrl(realm))
      .sendForm(requestBody));
  }

  public Future<HttpResponse<Buffer>> introspectToken(String realm, ClientCredentials client, String token) {
    var requestBody = prepareIntrospectRequestBody(client, token);
    var url = String.format("%s/realms/%s/protocol/openid-connect/token/introspect", properties.getUrl(), realm);
    return trace("keycloak.introspection", "POST", url, realm, null,
      () -> webClient.postAbs(url).sendForm(requestBody));
  }

  private String resolveTokenUrl(String realm) {
    return String.format("%s/realms/%s/protocol/openid-connect/token", properties.getUrl(), realm);
  }
}
