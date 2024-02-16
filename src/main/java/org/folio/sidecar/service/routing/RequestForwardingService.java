package org.folio.sidecar.service.routing;

import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.utils.RoutingUtils.getRequestId;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.InternalServerErrorException;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.WebClientProperties;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.sidecar.service.TransactionLogHandler;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class RequestForwardingService {

  /**
   * Predicate for removing headers from the request.
   *
   * <p>NOTE: This is used as a workaround to prevent errors with installing modules on a tenant.
   * HTTP client of mgr-tenant-entitlements is sending default user-agent within install call and due to this installing
   * of mod-circulation fails when it attempts to communicate with mod-pubsub (as vertex HTTP client tries to add its
   * own user-agent value...)
   */
  private static final Predicate<String> HEADERS_PREDICATE = header -> !USER_AGENT.equalsIgnoreCase(header);

  private final WebClient webClient;
  private final ErrorHandler errorHandler;
  private final SidecarSignatureService sidecarSignatureService;
  private final WebClientProperties webClientProperties;
  private final TransactionLogHandler transactionLogHandler;

  /**
   * Forwards incoming (ingress) or outgoing (egress) request.
   *
   * @param rc - {@link RoutingContext} object to forward request
   * @param absUri - absolute uri as {@link String} object
   */
  @SneakyThrows
  public void forward(RoutingContext rc, String absUri) {
    var request = rc.request();
    var headers = filterHeaders(request, HEADERS_PREDICATE);

    var bufferHttpRequest = webClient
      .requestAbs(request.method(), absUri)
      .timeout(webClientProperties.getTimeout())
      .putHeaders(headers)
      .putHeader(REQUEST_ID, getRequestId(rc));

    request.params().forEach(bufferHttpRequest::addQueryParam);

    bufferHttpRequest.sendStream(request)
      .onSuccess(resp -> {
        handleSuccessfulResponse(rc, resp);
        transactionLogHandler.handle(rc, resp, bufferHttpRequest);
      })
      .onFailure(error -> errorHandler.sendErrorResponse(
        rc, new InternalServerErrorException("Failed to proxy request", error)));
  }

  /**
   * Filters request headers with the given predicate.
   *
   * @param request request
   * @param headerPredicate predicate for filtering headers
   * @return filtered headers
   */
  private static HeadersMultiMap filterHeaders(HttpServerRequest request, Predicate<String> headerPredicate) {
    var headers = new HeadersMultiMap();
    for (var h : request.headers()) {
      if (headerPredicate.test(h.getKey())) {
        headers.add(h.getKey(), h.getValue());
      }
    }
    return headers;
  }

  private void handleSuccessfulResponse(RoutingContext rc, HttpResponse<Buffer> resp) {
    var response = rc.response();
    response.headers().addAll(resp.headers());
    response.setStatusCode(resp.statusCode());

    removeSidecarSignatureThenEndResponse(resp, response);
  }

  /**
   * Removes sidecar signature from the response and ends it.
   *
   * @param resp - {@link HttpResponse} object
   * @param response - {@link HttpServerResponse} object
   */
  private void removeSidecarSignatureThenEndResponse(HttpResponse<Buffer> resp, HttpServerResponse response) {
    sidecarSignatureService.removeSignature(response);
    var responseBodyBuffer = resp.bodyAsBuffer();
    if (responseBodyBuffer == null) {
      response.end();
      return;
    }

    response.end(responseBodyBuffer);
  }
}
