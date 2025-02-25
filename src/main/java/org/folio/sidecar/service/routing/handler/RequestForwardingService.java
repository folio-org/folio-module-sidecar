package org.folio.sidecar.service.routing.handler;

import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static java.lang.String.format;
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
import jakarta.inject.Named;
import jakarta.ws.rs.InternalServerErrorException;
import java.net.URI;
import java.util.function.Predicate;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.HttpProperties;
import org.folio.sidecar.configuration.properties.WebClientConfig;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.sidecar.service.TransactionLogHandler;

@Log4j2
@ApplicationScoped
class RequestForwardingService {

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
  private final WebClient webClientEgress;
  private final WebClient webClientGateway;
  private final ErrorHandler errorHandler;
  private final SidecarSignatureService sidecarSignatureService;
  private final HttpProperties httpProperties;
  private final WebClientConfig webClientConfig;
  private final TransactionLogHandler transactionLogHandler;

  RequestForwardingService(@Named("webClient") WebClient webClient,
    @Named("webClientEgress") WebClient webClientEgress, @Named("webClientGateway") WebClient webClientGateway,
    ErrorHandler errorHandler, SidecarSignatureService sidecarSignatureService, HttpProperties httpProperties,
    WebClientConfig webClientConfig, TransactionLogHandler transactionLogHandler) {
    this.webClient = webClient;
    this.webClientEgress = webClientEgress;
    this.webClientGateway = webClientGateway;
    this.errorHandler = errorHandler;
    this.sidecarSignatureService = sidecarSignatureService;
    this.httpProperties = httpProperties;
    this.webClientConfig = webClientConfig;
    this.transactionLogHandler = transactionLogHandler;
  }

  /**
   * Forwards incoming (ingress) request.
   *
   * @param rc - {@link RoutingContext} object to forward request
   * @param absUri - absolute uri as {@link String} object
   */
  @SneakyThrows
  public void forwardIngress(RoutingContext rc, String absUri) {
    forwardRequest(rc, absUri, webClient);
  }

  /**
   * Forwards outgoing (egress) request under HTTPS if TLS is enabled.
   *
   * @param rc - {@link RoutingContext} object to forward request
   * @param absUri - absolute uri as {@link String} object
   */
  @SneakyThrows
  public void forwardEgress(RoutingContext rc, String absUri) {
    if (webClientConfig.egress().tls().enabled()) {
      absUri = toHttpsUri(absUri);
    }
    forwardRequest(rc, absUri, webClientEgress);
  }

  /**
   * Forwards outgoing (egress) request to Gateway under HTTPS if TLS is enabled.
   *
   * @param rc - {@link RoutingContext} object to forward request
   * @param absUri - absolute uri as {@link String} object
   */
  @SneakyThrows
  public void forwardToGateway(RoutingContext rc, String absUri) {
    if (webClientConfig.gateway().tls().enabled()) {
      absUri = toHttpsUri(absUri);
    }
    forwardRequest(rc, absUri, webClientGateway);
  }

  private void forwardRequest(RoutingContext rc, String absUri, WebClient webClient) {
    var request = rc.request();

    var bufferHttpRequest = webClient
      .requestAbs(request.method(), absUri)
      .timeout(httpProperties.getTimeout())
      .putHeaders(filterHeaders(request))
      .putHeader(REQUEST_ID, getRequestId(rc));

    request.params().forEach(bufferHttpRequest::addQueryParam);

    bufferHttpRequest.sendStream(request)
      .onSuccess(resp -> {
        handleSuccessfulResponse(rc, resp);
        transactionLogHandler.log(rc, resp, bufferHttpRequest);
      })
      .onFailure(error -> errorHandler.sendErrorResponse(
        rc, new InternalServerErrorException("Failed to proxy request", error)));
  }

  private String toHttpsUri(String uri) {
    URI httpUri = URI.create(uri);
    return format("https://%s:%s%s", httpUri.getHost(), httpUri.getPort(), httpUri.getPath());
  }

  /**
   * Filters request headers with the given predicate.
   *
   * @param request request
   * @return filtered headers
   */
  private static HeadersMultiMap filterHeaders(HttpServerRequest request) {
    var headers = new HeadersMultiMap();
    for (var h : request.headers()) {
      if (HEADERS_PREDICATE.test(h.getKey())) {
        headers.set(h.getKey(), h.getValue());
      }
    }
    return headers;
  }

  private void handleSuccessfulResponse(RoutingContext rc, HttpResponse<Buffer> resp) {
    var response = rc.response();
    response.headers().addAll(resp.headers());
    response.setStatusCode(resp.statusCode());
    rc.addHeadersEndHandler(event -> rc.put("uht", System.currentTimeMillis()));

    removeSidecarSignatureThenEndResponse(resp, response, rc);
  }

  /**
   * Removes sidecar signature from the response and ends it.
   *
   * @param resp - {@link HttpResponse} object
   * @param response - {@link HttpServerResponse} object
   */
  private void removeSidecarSignatureThenEndResponse(HttpResponse<Buffer> resp, HttpServerResponse response,
    RoutingContext rc) {
    sidecarSignatureService.removeSignature(response);
    var responseBodyBuffer = resp.bodyAsBuffer();
    rc.addBodyEndHandler(event -> rc.put("urt", System.currentTimeMillis()));
    if (responseBodyBuffer == null) {
      response.end();
      return;
    }

    response.end(responseBodyBuffer);
  }
}
