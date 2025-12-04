package org.folio.sidecar.service;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.ThreadContext;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@UnitTest
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionLogHandlerTest {

  @InjectMocks
  private TransactionLogHandler transactionLogHandler;

  @AfterEach
  void tearDown() {
    ThreadContext.clearMap();
  }

  @Test
  void log_positive_allTimingFieldsPopulated() {
    var routingContext = mock(RoutingContext.class);
    var httpServerRequest = mock(HttpServerRequest.class);
    var socketAddress = mock(SocketAddress.class);

    long startTime = System.currentTimeMillis() - 1000;
    long connectTime = startTime + 50;
    long headerTime = startTime + 150;
    long responseTime = startTime + 500;

    when(routingContext.request()).thenReturn(httpServerRequest);
    when(routingContext.get("rt")).thenReturn(startTime);
    when(routingContext.get("uct")).thenReturn(connectTime);
    when(routingContext.get("uht")).thenReturn(headerTime);
    when(routingContext.get("urt")).thenReturn(responseTime);

    when(httpServerRequest.remoteAddress()).thenReturn(socketAddress);
    when(socketAddress.toString()).thenReturn("192.168.1.1:8080");
    when(httpServerRequest.authority()).thenReturn(null);
    when(httpServerRequest.method()).thenReturn(HttpMethod.POST);
    when(httpServerRequest.path()).thenReturn("/foo/entities");
    when(httpServerRequest.uri()).thenReturn("/foo/entities?query=test");
    when(httpServerRequest.version()).thenReturn(HttpVersion.HTTP_1_1);
    when(httpServerRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpServerRequest.getHeader("X-Remote-User")).thenReturn("test-user");
    when(httpServerRequest.getHeader(HttpHeaders.USER_AGENT.toString())).thenReturn("test-agent");
    when(httpServerRequest.getHeader(OkapiHeaders.TENANT)).thenReturn("diku");
    when(httpServerRequest.getHeader(OkapiHeaders.USER_ID)).thenReturn("user-123");

    final var httpClientRequest = mock(HttpClientRequest.class);
    final var requestHeaders = new HeadersMultiMap();
    requestHeaders.add(OkapiHeaders.REQUEST_ID, "12345/foo");
    when(httpClientRequest.headers()).thenReturn(requestHeaders);

    final var httpClientResponse = mock(HttpClientResponse.class);
    when(httpClientResponse.statusCode()).thenReturn(200);

    transactionLogHandler.log(routingContext, httpClientResponse, httpClientRequest);

    // Verify all timing fields were accessed from routing context
    // This confirms they were used to calculate the timing values
    verify(routingContext, atLeastOnce()).get("rt");
    verify(routingContext, atLeastOnce()).get("uct");
    verify(routingContext, atLeastOnce()).get("uht");
    verify(routingContext, atLeastOnce()).get("urt");
  }

  @Test
  void log_positive_withForwardedFor() {
    var routingContext = mock(RoutingContext.class);
    var httpServerRequest = mock(HttpServerRequest.class);

    long startTime = System.currentTimeMillis() - 100;

    when(routingContext.request()).thenReturn(httpServerRequest);
    when(routingContext.get("rt")).thenReturn(startTime);
    when(routingContext.get("uct")).thenReturn(startTime + 10);
    when(routingContext.get("uht")).thenReturn(startTime + 20);
    when(routingContext.get("urt")).thenReturn(startTime + 50);

    when(httpServerRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
    when(httpServerRequest.getHeader("X-Remote-User")).thenReturn(null);
    when(httpServerRequest.getHeader(HttpHeaders.USER_AGENT.toString())).thenReturn(null);
    when(httpServerRequest.getHeader(OkapiHeaders.TENANT)).thenReturn(null);
    when(httpServerRequest.getHeader(OkapiHeaders.USER_ID)).thenReturn(null);
    when(httpServerRequest.authority()).thenReturn(null);
    when(httpServerRequest.method()).thenReturn(HttpMethod.GET);
    when(httpServerRequest.path()).thenReturn("/test");
    when(httpServerRequest.uri()).thenReturn("/test");
    when(httpServerRequest.version()).thenReturn(HttpVersion.HTTP_1_1);

    final var httpClientRequest = mock(HttpClientRequest.class);
    final var requestHeaders = new HeadersMultiMap();
    requestHeaders.add(OkapiHeaders.REQUEST_ID, "67890/test");
    when(httpClientRequest.headers()).thenReturn(requestHeaders);

    final var httpClientResponse = mock(HttpClientResponse.class);
    when(httpClientResponse.statusCode()).thenReturn(404);

    transactionLogHandler.log(routingContext, httpClientResponse, httpClientRequest);

    // Verify X-Forwarded-For was processed
    verify(httpServerRequest).getHeader("X-Forwarded-For");
    // Verify all timing fields were accessed
    verify(routingContext, atLeastOnce()).get("rt");
    verify(routingContext, atLeastOnce()).get("uct");
    verify(routingContext, atLeastOnce()).get("uht");
    verify(routingContext, atLeastOnce()).get("urt");
  }

  @Test
  void log_positive_missingTimingFields() {
    var routingContext = mock(RoutingContext.class);
    var httpServerRequest = mock(HttpServerRequest.class);
    var socketAddress = mock(SocketAddress.class);

    when(routingContext.request()).thenReturn(httpServerRequest);
    // Only rt is present, others are null (simulating the bug scenario before the fix)
    when(routingContext.get("rt")).thenReturn(System.currentTimeMillis() - 100);
    when(routingContext.get("uct")).thenReturn(null);
    when(routingContext.get("uht")).thenReturn(null);
    when(routingContext.get("urt")).thenReturn(null);

    when(httpServerRequest.remoteAddress()).thenReturn(socketAddress);
    when(socketAddress.toString()).thenReturn("127.0.0.1:9000");
    when(httpServerRequest.authority()).thenReturn(null);
    when(httpServerRequest.method()).thenReturn(HttpMethod.PUT);
    when(httpServerRequest.path()).thenReturn("/api/test");
    when(httpServerRequest.uri()).thenReturn("/api/test");
    when(httpServerRequest.version()).thenReturn(HttpVersion.HTTP_1_1);
    when(httpServerRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpServerRequest.getHeader("X-Remote-User")).thenReturn(null);
    when(httpServerRequest.getHeader(HttpHeaders.USER_AGENT.toString())).thenReturn(null);
    when(httpServerRequest.getHeader(OkapiHeaders.TENANT)).thenReturn(null);
    when(httpServerRequest.getHeader(OkapiHeaders.USER_ID)).thenReturn(null);

    final var httpClientRequest = mock(HttpClientRequest.class);
    final var requestHeaders = new HeadersMultiMap();
    requestHeaders.add(OkapiHeaders.REQUEST_ID, "00000/api");
    when(httpClientRequest.headers()).thenReturn(requestHeaders);

    final var httpClientResponse = mock(HttpClientResponse.class);
    when(httpClientResponse.statusCode()).thenReturn(500);

    transactionLogHandler.log(routingContext, httpClientResponse, httpClientRequest);

    // Verify all timing fields were attempted to be retrieved
    // Even when null, the calculateValue method handles them gracefully
    verify(routingContext, atLeastOnce()).get("rt");
    verify(routingContext, atLeastOnce()).get("uct");
    verify(routingContext, atLeastOnce()).get("uht");
    verify(routingContext, atLeastOnce()).get("urt");
  }

  @Test
  void log_positive_partiallyMissingTimingFields() {
    var routingContext = mock(RoutingContext.class);
    var httpServerRequest = mock(HttpServerRequest.class);
    var socketAddress = mock(SocketAddress.class);

    long startTime = System.currentTimeMillis() - 100;

    when(routingContext.request()).thenReturn(httpServerRequest);
    // rt and uct present, but uht and urt missing (simulating old bug state)
    when(routingContext.get("rt")).thenReturn(startTime);
    when(routingContext.get("uct")).thenReturn(startTime + 10);
    when(routingContext.get("uht")).thenReturn(null);
    when(routingContext.get("urt")).thenReturn(null);

    when(httpServerRequest.remoteAddress()).thenReturn(socketAddress);
    when(socketAddress.toString()).thenReturn("172.17.0.1:5000");
    when(httpServerRequest.authority()).thenReturn(null);
    when(httpServerRequest.method()).thenReturn(HttpMethod.DELETE);
    when(httpServerRequest.path()).thenReturn("/resources/123");
    when(httpServerRequest.uri()).thenReturn("/resources/123");
    when(httpServerRequest.version()).thenReturn(HttpVersion.HTTP_1_1);
    when(httpServerRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpServerRequest.getHeader("X-Remote-User")).thenReturn(null);
    when(httpServerRequest.getHeader(HttpHeaders.USER_AGENT.toString())).thenReturn(null);
    when(httpServerRequest.getHeader(OkapiHeaders.TENANT)).thenReturn(null);
    when(httpServerRequest.getHeader(OkapiHeaders.USER_ID)).thenReturn(null);

    final var httpClientRequest = mock(HttpClientRequest.class);
    final var requestHeaders = new HeadersMultiMap();
    requestHeaders.add(OkapiHeaders.REQUEST_ID, "99999/resources");
    when(httpClientRequest.headers()).thenReturn(requestHeaders);

    final var httpClientResponse = mock(HttpClientResponse.class);
    when(httpClientResponse.statusCode()).thenReturn(204);

    transactionLogHandler.log(routingContext, httpClientResponse, httpClientRequest);

    // Verify all timing fields were attempted to be retrieved
    verify(routingContext, atLeastOnce()).get("rt");
    verify(routingContext, atLeastOnce()).get("uct");
    verify(routingContext, atLeastOnce()).get("uht");
    verify(routingContext, atLeastOnce()).get("urt");
  }
}
