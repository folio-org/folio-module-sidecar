package org.folio.sidecar.integration.keycloak;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.UnauthorizedException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.cred.model.ClientCredentials;
import org.folio.sidecar.integration.cred.model.UserCredentials;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.service.ErrorHandler;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class KeycloakService {

  private final KeycloakClient keycloakClient;
  private final ErrorHandler errorHandler;

  public Future<TokenResponse> obtainUserToken(String realm, ClientCredentials client, UserCredentials user) {
    return obtainToken(() -> keycloakClient.obtainUserToken(realm, client, user));
  }

  public Future<TokenResponse> refreshUserToken(String realm, ClientCredentials client, String refreshToken) {
    return obtainToken(() -> keycloakClient.refreshUserToken(realm, client, refreshToken));
  }

  public Future<TokenResponse> obtainToken(String realm, ClientCredentials client) {
    return obtainToken(() -> keycloakClient.obtainToken(realm, client));
  }

  public Future<TokenResponse> obtainToken(String realm, ClientCredentials client, RoutingContext rc) {
    return obtainToken(() -> keycloakClient.obtainToken(realm, client),
      authError -> errorHandler.sendErrorResponse(rc, authError),
      clientError -> errorHandler.sendErrorResponse(rc, clientError));
  }

  private Future<TokenResponse> obtainToken(Supplier<Future<HttpResponse<Buffer>>> tokenLoader,
    Handler<UnauthorizedException> authErrorHandler,
    Handler<WebApplicationException> clientErrorHandler) {
    var promise = Promise.<TokenResponse>promise();
    tokenLoader.get()
      .onSuccess(handleResponse(authErrorHandler, promise))
      .onFailure(handleClientError(clientErrorHandler, promise));
    return promise.future();
  }

  private Future<TokenResponse> obtainToken(Supplier<Future<HttpResponse<Buffer>>> tokenLoader) {
    return obtainToken(tokenLoader, log::warn, log::warn);
  }

  private Handler<HttpResponse<Buffer>> handleResponse(
    Handler<UnauthorizedException> authErrorHandler, Promise<TokenResponse> promise) {
    return response -> {
      if (response.statusCode() == HttpResponseStatus.OK.code()) {
        var token = parseToken(response);
        promise.complete(token);
      } else {
        var error = new UnauthorizedException("Authentication error: " + response.bodyAsString());
        authErrorHandler.handle(error);
        promise.fail(error);
      }
    };
  }

  private static Handler<Throwable> handleClientError(Handler<WebApplicationException> clientErrorHandler,
    Promise<TokenResponse> promise) {
    return error -> {
      var clientError = new WebApplicationException("Unable to obtain service user token", error);
      clientErrorHandler.handle(clientError);
      promise.fail(clientError);
    };
  }

  private TokenResponse parseToken(HttpResponse<Buffer> response) {
    return response.bodyAsJson(TokenResponse.class);
  }
}
