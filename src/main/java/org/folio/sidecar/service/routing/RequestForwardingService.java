package org.folio.sidecar.service.routing;

import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static java.lang.String.format;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.utils.RoutingUtils.getRequestId;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.InternalServerErrorException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
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

  private final HttpClient httpClient;
  private final HttpClient httpClientEgress;
  private final HttpClient httpClientGateway;
  private final ErrorHandler errorHandler;
  private final SidecarSignatureService sidecarSignatureService;
  private final HttpProperties httpProperties;
  private final WebClientConfig webClientConfig;
  private final TransactionLogHandler transactionLogHandler;

  public RequestForwardingService(@Named("httpClient") HttpClient httpClient,
    @Named("httpClientEgress") HttpClient httpClientEgress, @Named("httpClientGateway") HttpClient httpClientGateway,
    ErrorHandler errorHandler, SidecarSignatureService sidecarSignatureService, HttpProperties httpProperties,
    WebClientConfig webClientConfig, TransactionLogHandler transactionLogHandler) {
    this.httpClient = httpClient;
    this.httpClientEgress = httpClientEgress;
    this.httpClientGateway = httpClientGateway;
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
    forwardRequest(rc, absUri, httpClient);
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
    forwardRequest(rc, absUri, httpClientEgress);
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
    forwardRequest(rc, absUri, httpClientGateway);
  }

  private void forwardRequest(RoutingContext rc, String absUri, HttpClient httpClient) {

    HttpServerRequest httpServerRequest = rc.request();
    URI httpUri = URI.create(absUri);

    // Create an HTTP request
    Future<HttpClientRequest> httpClientRequestFuture = httpClient.request(httpServerRequest.method(),
        httpUri.getPort(), httpUri.getHost(), httpUri.getPath())
        .timeout(httpProperties.getTimeout(), TimeUnit.MILLISECONDS);
    httpClientRequestFuture.onSuccess(httpClientRequest -> {

      httpClientRequest.headers().addAll(filterHeaders(httpServerRequest));
      httpClientRequest.headers().add(REQUEST_ID, getRequestId(rc));

      // Set the maximum write queue size to prevent memory overflow
      httpClientRequest.setWriteQueueMaxSize(128 * 1024); // 128 KB buffer

      // Attach drainHandler to resume reading when the queue has space
      httpClientRequest.drainHandler(v -> {
        log.trace("Write queue has space again, resuming  read from server requests.");
        httpServerRequest.resume();
      });

      // If the write queue is full, pause the ReadStream
      httpServerRequest.handler(buffer -> {
        if (httpClientRequest.writeQueueFull()) {
          httpServerRequest.pause();
        }
        httpClientRequest.write(buffer);
      });

      //Handle the HTTP client response by streaming the output back to the server.
      httpClientRequest.response().onSuccess(response -> {
        log.trace("Handle the HTTP client response by streaming the output back to the server");
        handleSuccessfulResponse(rc, response);
        transactionLogHandler.log(rc, response, httpClientRequest);
      });
      // End the request when the file stream finishes
      httpServerRequest.endHandler(v -> {
        log.trace("End the request when the file stream finishes");
        httpClientRequest.end();
      });
    }).onFailure(error -> errorHandler.sendErrorResponse(
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

  private void handleSuccessfulResponse(RoutingContext rc, HttpClientResponse resp) {
    var response = rc.response();
    response.headers().addAll(resp.headers());
    response.setStatusCode(resp.statusCode());
    rc.addHeadersEndHandler(event -> rc.put("uht", System.currentTimeMillis()));

    removeSidecarSignatureThenEndResponse(resp, response);
  }

  /**
   * Removes sidecar signature from the response and ends it.
   *
   * @param httpClientResponse - {@link HttpResponse} object
   * @param httpServerResponse - {@link HttpServerResponse} object
   */
  private void removeSidecarSignatureThenEndResponse(HttpClientResponse httpClientResponse,
    HttpServerResponse httpServerResponse) {
    sidecarSignatureService.removeSignature(httpServerResponse);

    // Set the maximum write queue size to prevent memory overflow
    httpServerResponse.setWriteQueueMaxSize(128 * 1024); // 128 KB buffer

    // Attach drainHandler to resume reading when the queue has space
    httpServerResponse.drainHandler(v -> {
      log.trace("Write queue has space again, resuming  read.");
      httpClientResponse.resume();
    });

    // If the write queue is full, pause the ReadStream
    httpClientResponse.handler(buffer -> {
      if (httpServerResponse.writeQueueFull()) {
        httpClientResponse.pause();
      }
      httpServerResponse.write(buffer);
    });

    // End the request when the file stream finishes
    httpClientResponse.endHandler(v -> {
      log.trace("Response to the server  complete, ending request.");
      httpServerResponse.end();
    });
  }
}
