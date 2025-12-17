package org.folio.sidecar.service.routing.handler;

import static java.lang.String.format;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.sidecar.service.ErrorHandler;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
class NotFoundHandler {

  @Named("notFoundHandler")
  @ApplicationScoped
  static ChainedHandler getInstance(ErrorHandler errorHandler) {
    return ChainedHandler.as(rc -> {
      var rq = rc.request();
      logDiagnosticInfo(rc, rq);
      errorHandler.sendErrorResponse(rc, notFoundError(rq));
    });
  }

  private static void logDiagnosticInfo(RoutingContext rc, HttpServerRequest rq) {
    var hasSidecarSignature = rc.get("SELF_REQUEST_KEY");
    if (hasSidecarSignature == null || Boolean.FALSE.equals(hasSidecarSignature)) {
      System.err.println("================================================================================");
      System.err.println("CRITICAL: Route Not Found for External Request");
      System.err.println("================================================================================");
      System.err.println("Request: " + rq.method() + " " + rq.path());
      System.err.println("This sidecar could not route the request because:");
      System.err.println("1. No ingress route matched (not a request for this module's endpoints)");
      System.err.println("2. No egress route matched (not module-to-module communication)");
      System.err.println("");
      System.err.println("LIKELY CAUSE: Kong/Gateway routing misconfiguration!");
      System.err.println("External traffic MUST route to main application port, NOT sidecar port:");
      System.err.println("  Correct:   Kong -> Service:80 -> Main App Container:8081");
      System.err.println("  Wrong:     Kong -> Service:8082 -> Sidecar Container:8082");
      System.err.println("For Telepresence users: This configuration causes intercepts to be bypassed!");
      System.err.println("Reference: https://folio-org.atlassian.net/browse/RANCHER-2623");
      System.err.println("================================================================================");
    }
  }

  private static NotFoundException notFoundError(HttpServerRequest rq) {
    return new NotFoundException(format("Route is not found [method: %s, path: %s]", rq.method(), rq.path()));
  }
}
