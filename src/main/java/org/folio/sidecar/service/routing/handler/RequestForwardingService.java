package org.folio.sidecar.service.routing.handler;

import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static java.lang.String.format;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.utils.RoutingUtils.getRequestId;

import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.InternalServerErrorException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.HttpProperties;
import org.folio.sidecar.configuration.properties.WebClientConfig;
import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.sidecar.service.TransactionLogHandler;

@Log4j2
@ApplicationScoped
public class RequestForwardingService {

  private final HttpClient httpClient;
  private final HttpClient httpClientEgress;
  private final HttpClient httpClientGateway;
  private final SidecarSignatureService sidecarSignatureService;
  private final HttpProperties httpProperties;
  private final WebClientConfig webClientConfig;
  private final TransactionLogHandler transactionLogHandler;

  public RequestForwardingService(@Named("httpClient") HttpClient httpClient,
    @Named("httpClientEgress") HttpClient httpClientEgress, @Named("httpClientGateway") HttpClient httpClientGateway,
    SidecarSignatureService sidecarSignatureService, HttpProperties httpProperties,
    WebClientConfig webClientConfig, TransactionLogHandler transactionLogHandler) {
    this.httpClient = httpClient;
    this.httpClientEgress = httpClientEgress;
    this.httpClientGateway = httpClientGateway;
    this.sidecarSignatureService = sidecarSignatureService;
    this.httpProperties = httpProperties;
    this.webClientConfig = webClientConfig;
    this.transactionLogHandler = transactionLogHandler;
  }

  /**
   * Forwards incoming (ingress) request.
   *
   * @param rc      - {@link RoutingContext} object to forward request
   * @param absUri  - absolute uri as {@link String} object
   */
  @SneakyThrows
  public Future<Void> forwardIngress(RoutingContext rc, String absUri) {
    return forwardRequest(rc, absUri, httpClient);
  }

  /**
   * Forwards outgoing (egress) request under HTTPS if TLS is enabled.
   *
   * @param rc      - {@link RoutingContext} object to forward request
   * @param absUri  - absolute uri as {@link String} object
   */
  @SneakyThrows
  public Future<Void> forwardEgress(RoutingContext rc, String absUri) {
    if (webClientConfig.egress().tls().enabled()) {
      absUri = toHttpsUri(absUri);
    }
    return forwardRequest(rc, absUri, httpClientEgress);
  }

  /**
   * Forwards outgoing (egress) request to Gateway under HTTPS if TLS is enabled.
   *
   * @param rc      - {@link RoutingContext} object to forward request
   * @param absUri  - absolute uri as {@link String} object
   */
  @SneakyThrows
  public Future<Void> forwardToGateway(RoutingContext rc, String absUri) {
    if (webClientConfig.gateway().tls().enabled()) {
      absUri = toHttpsUri(absUri);
    }
    return forwardRequest(rc, absUri, httpClientGateway);
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private Future<Void> forwardRequest(RoutingContext rc, String absUri, HttpClient httpClient) {
    var result = Promise.<Void>promise();

    HttpServerRequest httpServerRequest = rc.request();
    URI httpUri = URI.create(absUri);

    QueryStringEncoder encoder = new QueryStringEncoder(httpUri.getPath());
    httpServerRequest.params().forEach(encoder::addParam);

    // Create an HTTP request
    Future<HttpClientRequest> request = createHttpClientRequestFuture(httpClient, httpServerRequest, httpUri, encoder)
      .timeout(httpProperties.getTimeout(), TimeUnit.MILLISECONDS);
    request.onSuccess(httpClientRequest -> {

      httpClientRequest.headers().setAll(filterHeaders(httpServerRequest));
      httpClientRequest.headers().set(REQUEST_ID, getRequestId(rc));

      // Set the maximum write queue size to prevent memory overflow
      httpClientRequest.setWriteQueueMaxSize(128 * 1024); // 128 KB buffer

      // Attach drainHandler to resume reading when the queue has space
      httpClientRequest.drainHandler(v -> {
        log.trace("Write queue has space again, resuming  read from server requests.");
        httpServerRequest.resume();
      });

      // If the write queue is full, pause the ReadStream
      Set<HttpMethod> nonBodyMethods = Set.of(HttpMethod.GET, HttpMethod.HEAD);
      if (!nonBodyMethods.contains(httpServerRequest.method())) {
        httpClientRequest.setChunked(true);
        httpServerRequest.handler(buffer -> {
          if (httpClientRequest.writeQueueFull()) {
            httpServerRequest.pause();
          }
          httpClientRequest.write(buffer);
        });
      }

      try {
        // End the request when the file stream finishes
        httpServerRequest.endHandler(v -> {
          log.trace("End the request when the file stream finishes");
          httpClientRequest.end();
        });
      } catch (Exception e) {
        log.warn("The request has already been read, but the HTTP client still needs to be closed. ", e);
        httpClientRequest.end();
      }

      //Handle the HTTP client response by streaming the output back to the server.
      httpClientRequest.response()
        .timeout(httpProperties.getTimeout(), TimeUnit.MILLISECONDS)
        .onSuccess(response -> {
          log.trace("Handle the HTTP client response by streaming the output back to the server");
          handleSuccessfulResponse(rc, response, result);
          transactionLogHandler.log(rc, response, httpClientRequest);
        }).onFailure(error -> {
          var errorMessage = format("Failed to proxy request because of response error: %s", error.getMessage());
          log.error(errorMessage);
          result.fail(new InternalServerErrorException(errorMessage, error));
        });
    }).onFailure(error -> {
      var errorMessage = format("Failed to proxy request: %s", error.getMessage());
      log.error(errorMessage);
      result.fail(new InternalServerErrorException(errorMessage, error));
    });
    return result.future();
  }

  private static Future<HttpClientRequest> createHttpClientRequestFuture(HttpClient httpClient,
    HttpServerRequest httpServerRequest, URI httpUri, QueryStringEncoder encoder) {

    var requestOptions = new RequestOptions()
      .setHost(httpUri.getHost())
      .setPort(getPortOrElseDefault(httpUri))
      .setURI(encoder.toString())
      .setMethod(httpServerRequest.method());

    if (httpUri.getScheme() != null && "https".equalsIgnoreCase(httpUri.getScheme())) {
      requestOptions.setSsl(true);
    }

    return httpClient.request(requestOptions);
  }

  private String toHttpsUri(String uri) {
    var httpUri = URI.create(uri);
    return httpUri.getPort() == -1
      ? format("https://%s%s", httpUri.getHost(), httpUri.getPath())
      : format("https://%s:%s%s", httpUri.getHost(), httpUri.getPort(), httpUri.getPath());
  }

  /**
   * Filters request headers to exclude User-Agent and Priority.
   *
   * @param request request
   * @return filtered headers
   */
  private static MultiMap filterHeaders(HttpServerRequest request) {
    var headers = new HeadersMultiMap().setAll(request.headers());

    /*
     * This is used as a workaround to prevent errors with installing modules on a tenant.
     * HTTP client of mgr-tenant-entitlements is sending default user-agent within install call and due to this
     * installing of mod-circulation fails when it attempts to communicate with mod-pubsub (as vertex HTTP client
     * tries to add its own user-agent value...)
     */
    headers.remove(USER_AGENT);

    /*
     * Workaround to protect modules against the DoS via HTTP priority header vulnerability CVE-2025-31650:
     * <a href="https://folio-org.atlassian.net/browse/FOLIO-4316">FOLIO-4316</a>
     */
    headers.remove("Priority");

    return headers;
  }

  private void handleSuccessfulResponse(RoutingContext rc, HttpClientResponse resp, Promise<Void> result) {
    var response = rc.response();
    response.headers().addAll(resp.headers());
    response.setStatusCode(resp.statusCode());
    rc.put("uht", System.currentTimeMillis());

    removeSidecarSignatureThenEndResponse(rc, resp, response, result);
  }

  /**
   * Removes sidecar signature from the response and ends it.
   *
   * @param rc                 - {@link RoutingContext} object
   * @param httpClientResponse - {@link HttpResponse} object
   * @param httpServerResponse - {@link HttpServerResponse} object
   * @param result             - result promise
   */
  private void removeSidecarSignatureThenEndResponse(RoutingContext rc, HttpClientResponse httpClientResponse,
    HttpServerResponse httpServerResponse, Promise<Void> result) {
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
      rc.put("urt", System.currentTimeMillis());
      httpServerResponse.end();
      result.complete();
    });

    httpClientResponse.exceptionHandler(error ->
      result.fail(new InternalServerErrorException("Failed to proxy request: upstream issue", error)));
  }

  private static int getPortOrElseDefault(URI httpUri) {
    int port;
    if (httpUri.getPort() == -1 && "http".equalsIgnoreCase(httpUri.getScheme())) {
      port = 80;
    } else if (httpUri.getPort() == -1 && "https".equalsIgnoreCase(httpUri.getScheme())) {
      port = 443;
    } else {
      port = httpUri.getPort();
    }
    return port;
  }
}
