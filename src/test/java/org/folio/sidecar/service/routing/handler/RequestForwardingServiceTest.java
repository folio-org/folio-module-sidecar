package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.POST;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.handler.codec.http.QueryStringEncoder;
import io.smallrye.mutiny.TimeoutException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.ws.rs.InternalServerErrorException;
import java.util.function.Consumer;
import org.folio.sidecar.configuration.properties.HttpProperties;
import org.folio.sidecar.configuration.properties.WebClientConfig;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.sidecar.service.TransactionLogHandler;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RequestForwardingServiceTest {

  public static final long TIMEOUT = 5000L;
  private static final String PATH = "/foo/entities";
  private final String absoluteUrl = TestConstants.MODULE_URL + PATH;

  @InjectMocks private RequestForwardingService service;
  @Mock private HttpClient httpClient;
  @Mock private HttpRequest<Buffer> httpRequest;

  @Mock private HttpClientRequest httpClientRequest;
  @Mock private HttpResponse<Buffer> httpResponse;
  @Mock private HttpClientResponse httpClientResponse;
  @Mock private MultiMap headers;

  @Mock private MultiMap headersResponse;
  @Mock private SidecarSignatureService sidecarSignatureService;
  @Mock private HttpProperties httpProperties;
  @Mock private WebClientConfig webClientConfig;
  @Mock private TransactionLogHandler transactionLogHandler;
  @Mock
  private Buffer buffer;
  @Captor private ArgumentCaptor<MultiMap> requestHeadersMapCaptor;
  @Captor private ArgumentCaptor<MultiMap> responseHeadersMapCaptor;
  @Captor private ArgumentCaptor<Handler<Void>> requestDrainHandlerCaptor;
  @Captor private ArgumentCaptor<Handler<Void>> responseDrainHandlerCaptor;
  @Captor private ArgumentCaptor<Handler<Buffer>> requestHandlerCaptor;
  @Captor private ArgumentCaptor<Handler<Buffer>> responseHandlerCaptor;
  @Captor private ArgumentCaptor<String> queryParamCaptor;
  @Captor private ArgumentCaptor<String> headersCaptor;
  @Captor private ArgumentCaptor<String> requestIdCaptor;
  @Captor private ArgumentCaptor<Handler<Void>> requestEndHandlerCaptor;
  @Captor private ArgumentCaptor<Handler<Void>> responseEndHandlerCaptor;

  @Test
  void forward_positive() {
    var routingContext = routingContext(RequestForwardingServiceTest::withHttpResponse);
    var encoder = new QueryStringEncoder(PATH);
    routingContext.request().params().forEach(encoder::addParam);

    when(httpClient.request(argThat(options ->
      "sc-foo".equals(options.getHost())
        && 8081 == options.getPort()
        && encoder.toString().equals(options.getURI())
        && POST == options.getMethod())))
      .thenReturn(Future.succeededFuture(httpClientRequest));
    prepareHttpRequestMocks(routingContext, httpClientRequest);
    prepareHttpResponseMocks(routingContext, httpClientResponse);

    var response = routingContext.response();
    when(response.headers()).thenReturn(headersResponse);
    when(headersResponse.addAll(responseHeadersMapCaptor.capture())).thenReturn(headersResponse);

    service.forwardIngress(routingContext, absoluteUrl);

    var capturedRequestHeaders = requestHeadersMapCaptor.getValue();
    assertThat(capturedRequestHeaders).hasSize(3);
    assertThat(capturedRequestHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TENANT)).isEqualTo(TestConstants.TENANT_ID);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TOKEN)).isEqualTo(TestConstants.AUTH_TOKEN);
    assertThat(capturedRequestHeaders.contains(USER_AGENT)).isFalse();

    var responseHeaders = responseHeadersMapCaptor.getValue();
    assertThat(responseHeaders).hasSize(2);
    assertThat(responseHeaders.get("tst-header")).isEqualTo("tst-value");
    assertThat(responseHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);
    assertThat(responseHeaders.get(TestConstants.SIDECAR_SIGNATURE_HEADER)).isNull();

    assertThat(requestIdCaptor.getValue()).isNotEmpty().matches("\\d{6}/foo");

    verify(sidecarSignatureService).removeSignature(any(HttpServerResponse.class));

    // Trigger the captured handler manually
    requestDrainHandlerCaptor.getValue().handle(null);
    // Verify that resume() was called on httpServerRequest
    verify(routingContext.request()).resume();

    // Simulate receiving a buffer
    requestHandlerCaptor.getValue().handle(buffer);
    // Verify httpServerRequest.pause() is called
    verify(routingContext.request()).pause();
    // Verify that the buffer is still written to httpClientRequest
    verify(httpClientRequest).write(buffer);

    // Trigger the captured handler manually
    requestEndHandlerCaptor.getValue().handle(null);
    // Verify that end() was called on httpClientRequest
    verify(httpClientRequest).end();

    // Trigger the captured handler manually
    responseDrainHandlerCaptor.getValue().handle(null);
    // Verify that resume() was called on httpServerRequest
    verify(httpClientResponse).resume();

    // Simulate receiving a buffer
    responseHandlerCaptor.getValue().handle(buffer);
    // Verify httpServerRequest.pause() is called
    verify(httpClientResponse).pause();
    // Verify that the buffer is still written to httpClientRequest
    verify(routingContext.response()).write(buffer);

    // Trigger the captured handler manually
    responseEndHandlerCaptor.getValue().handle(null);
    // Verify that end() was called on httpClientRequest
    verify(routingContext.response()).end();
  }

  @CsvSource({
    "http://sc-foo, false, 80",
    "https://sc-foo, true, 443",
    "http://sc-foo:8081, false, 8081",
    "https://sc-foo:8081, true, 8081"
  })
  @ParameterizedTest
  @MockitoSettings(strictness = Strictness.LENIENT)
  void forward_positive_urlHasNoPort(String baseUrl, boolean sslEnabled, int port) {
    var routingContext = routingContext(RequestForwardingServiceTest::withHttpResponse);
    QueryStringEncoder encoder = new QueryStringEncoder(PATH);
    routingContext.request().params().forEach(encoder::addParam);

    when(httpClient.request(argThat(options ->
      "sc-foo".equals(options.getHost())
      && port == options.getPort()
      && encoder.toString().equals(options.getURI())
      && POST == options.getMethod()
      && sslEnabled == isTrue(options.isSsl())
    ))).thenReturn(succeededFuture(httpClientRequest));

    prepareHttpRequestMocks(routingContext, httpClientRequest);
    prepareHttpResponseMocks(routingContext, httpClientResponse);

    var response = routingContext.response();
    when(response.headers()).thenReturn(headersResponse);
    when(headersResponse.addAll(responseHeadersMapCaptor.capture())).thenReturn(headersResponse);

    service.forwardIngress(routingContext, baseUrl + PATH);
    verify(httpClient).request(argThat(options ->
      "sc-foo".equals(options.getHost())
        && port == options.getPort()
        && encoder.toString().equals(options.getURI())
        && POST == options.getMethod()
        && sslEnabled == isTrue(options.isSsl())));
  }

  @Test
  void forwardEgress_positive() {
    var egressSettingsMock = mock(WebClientConfig.WebClientSettings.class);
    when(webClientConfig.egress()).thenReturn(egressSettingsMock);

    var egressTlsMock = mock(WebClientConfig.TlsSettings.class);
    when(egressSettingsMock.tls()).thenReturn(egressTlsMock);
    when(egressTlsMock.enabled()).thenReturn(true);
    var encoder = new QueryStringEncoder(PATH);
    var routingContext = routingContext(RequestForwardingServiceTest::withHttpResponse);
    routingContext.request().params().forEach(encoder::addParam);

    when(httpClient.request(argThat(options ->
      "sc-foo".equals(options.getHost())
        && 8081 == options.getPort()
        && encoder.toString().equals(options.getURI())
        && POST == options.getMethod())))
      .thenReturn(succeededFuture(httpClientRequest));

    prepareHttpRequestMocks(routingContext, httpClientRequest);
    prepareHttpResponseMocks(routingContext, httpClientResponse);

    var response = routingContext.response();
    when(response.headers()).thenReturn(headersResponse);
    when(headersResponse.addAll(responseHeadersMapCaptor.capture())).thenReturn(headersResponse);

    service.forwardEgress(routingContext, absoluteUrl);

    var capturedRequestHeaders = requestHeadersMapCaptor.getValue();
    assertThat(capturedRequestHeaders).hasSize(3);
    assertThat(capturedRequestHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TENANT)).isEqualTo(TestConstants.TENANT_ID);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TOKEN)).isEqualTo(TestConstants.AUTH_TOKEN);
    assertThat(capturedRequestHeaders.contains(USER_AGENT)).isFalse();

    var responseHeaders = responseHeadersMapCaptor.getValue();
    assertThat(responseHeaders).hasSize(2);
    assertThat(responseHeaders.get("tst-header")).isEqualTo("tst-value");
    assertThat(responseHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);
    assertThat(responseHeaders.get(TestConstants.SIDECAR_SIGNATURE_HEADER)).isNull();
    assertThat(requestIdCaptor.getValue()).isNotEmpty().matches("\\d{6}/foo");

    verify(sidecarSignatureService).removeSignature(any(HttpServerResponse.class));

    // Trigger the captured handler manually
    requestDrainHandlerCaptor.getValue().handle(null);
    // Verify that resume() was called on httpServerRequest
    verify(routingContext.request()).resume();

    // Simulate receiving a buffer
    requestHandlerCaptor.getValue().handle(buffer);
    // Verify httpServerRequest.pause() is called
    verify(routingContext.request()).pause();
    // Verify that the buffer is still written to httpClientRequest
    verify(httpClientRequest).write(buffer);

    // Trigger the captured handler manually
    requestEndHandlerCaptor.getValue().handle(null);
    // Verify that end() was called on httpClientRequest
    verify(httpClientRequest).end();

    // Trigger the captured handler manually
    responseDrainHandlerCaptor.getValue().handle(null);
    // Verify that resume() was called on httpServerRequest
    verify(httpClientResponse).resume();

    // Simulate receiving a buffer
    responseHandlerCaptor.getValue().handle(buffer);
    // Verify httpServerRequest.pause() is called
    verify(httpClientResponse).pause();
    // Verify that the buffer is still written to httpClientRequest
    verify(routingContext.response()).write(buffer);

    // Trigger the captured handler manually
    responseEndHandlerCaptor.getValue().handle(null);
    // Verify that end() was called on httpClientRequest
    verify(routingContext.response()).end();
  }

  @Test
  void forward_positive_nullBodyBuffer() {
    var routingContext = routingContext(RequestForwardingServiceTest::withHttpResponse);

    var encoder = new QueryStringEncoder(PATH);
    routingContext.request().params().forEach(encoder::addParam);

    when(httpClient.request(argThat(options ->
      "sc-foo".equals(options.getHost())
        && 8081 == options.getPort()
        && encoder.toString().equals(options.getURI())
        && POST == options.getMethod())))
      .thenReturn(Future.succeededFuture(httpClientRequest));
    prepareHttpRequestMocks(routingContext, httpClientRequest);
    prepareHttpResponseMocks(routingContext, httpClientResponse);

    var response = routingContext.response();
    when(response.headers()).thenReturn(headersResponse);
    when(headersResponse.addAll(responseHeadersMapCaptor.capture())).thenReturn(headersResponse);

    service.forwardIngress(routingContext, absoluteUrl);

    var capturedRequestHeaders = requestHeadersMapCaptor.getValue();
    assertThat(capturedRequestHeaders).hasSize(3);
    assertThat(capturedRequestHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TENANT)).isEqualTo(TestConstants.TENANT_ID);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TOKEN)).isEqualTo(TestConstants.AUTH_TOKEN);

    var responseHeaders = responseHeadersMapCaptor.getValue();
    assertThat(responseHeaders).hasSize(2);
    assertThat(responseHeaders.get("tst-header")).isEqualTo("tst-value");
    assertThat(responseHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);

    // assertThat(queryParamCaptor.getAllValues()).containsExactly("query", "name==test", "offset", "10", "size", "50");
    assertThat(requestIdCaptor.getValue()).isNotEmpty().matches("\\d{6}/foo");
    assertThat(responseHeaders.get(TestConstants.SIDECAR_SIGNATURE_HEADER)).isNull();

    verify(sidecarSignatureService).removeSignature(any(HttpServerResponse.class));

    // Trigger the captured handler manually
    requestDrainHandlerCaptor.getValue().handle(null);
    // Verify that resume() was called on httpServerRequest
    verify(routingContext.request()).resume();

    // Simulate receiving a buffer
    requestHandlerCaptor.getValue().handle(buffer);
    // Verify httpServerRequest.pause() is called
    verify(routingContext.request()).pause();
    // Verify that the buffer is still written to httpClientRequest
    verify(httpClientRequest).write(buffer);

    // Trigger the captured handler manually
    requestEndHandlerCaptor.getValue().handle(null);
    // Verify that end() was called on httpClientRequest
    verify(httpClientRequest).end();

    // Trigger the captured handler manually
    responseDrainHandlerCaptor.getValue().handle(null);
    // Verify that resume() was called on httpServerRequest
    verify(httpClientResponse).resume();

    // Simulate receiving a buffer
    responseHandlerCaptor.getValue().handle(buffer);
    // Verify httpServerRequest.pause() is called
    verify(httpClientResponse).pause();
    // Verify that the buffer is still written to httpClientRequest
    verify(routingContext.response()).write(buffer);

    // Trigger the captured handler manually
    responseEndHandlerCaptor.getValue().handle(null);
    // Verify that end() was called on httpClientRequest
    verify(routingContext.response()).end();
  }

  @Test
  void forward_negative() {
    var routingContext = routingContext(rc -> {});
    var error = new TimeoutException();

    QueryStringEncoder encoder = new QueryStringEncoder(PATH);
    routingContext.request().params().forEach(encoder::addParam);

    when(httpClient.request(argThat(options ->
      "sc-foo".equals(options.getHost())
        && 8081 == options.getPort()
        && encoder.toString().equals(options.getURI())
        && POST == options.getMethod())))
      .thenReturn(succeededFuture(httpClientRequest));
    when(httpProperties.getTimeout()).thenReturn(TIMEOUT);
    when(httpProperties.getTimeout()).thenReturn(TIMEOUT);
    when(httpClientRequest.headers()).thenReturn(headers);
    when(headers.setAll(requestHeadersMapCaptor.capture())).thenReturn(headers);
    when(headers.set(eq(OkapiHeaders.REQUEST_ID), requestIdCaptor.capture())).thenReturn(headers);
    when(httpClientRequest.response()).thenReturn(failedFuture(error));

    var result = service.forwardIngress(routingContext, absoluteUrl);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isInstanceOf(InternalServerErrorException.class);
  }

  private void prepareHttpResponseMocks(RoutingContext routingContext, HttpClientResponse httpClientResponse) {
    when(httpClientResponse.headers()).thenReturn(responseHeaders());
    when(httpClientResponse.statusCode()).thenReturn(SC_OK);

    HttpServerResponse httpServerResponse = routingContext.response();
    // Mock drainHandler method
    when(httpServerResponse.drainHandler(responseDrainHandlerCaptor.capture())).thenReturn(httpServerResponse);
    // Mock handler method
    when(httpClientResponse.pause()).thenReturn(httpClientResponse);
    doReturn(true).when(httpServerResponse).writeQueueFull(); // Simulate write queue being full
    when(httpServerResponse.write(any(Buffer.class))).thenReturn(succeededFuture());
    when(httpClientResponse.handler(responseHandlerCaptor.capture())).thenReturn(httpClientResponse);
    // Mock endHandler method
    when(httpClientResponse.endHandler(responseEndHandlerCaptor.capture())).thenReturn(httpClientResponse);
  }

  private void prepareHttpRequestMocks(RoutingContext routingContext, HttpClientRequest httpClientRequest) {
    when(httpProperties.getTimeout()).thenReturn(TIMEOUT);
    when(httpClientRequest.headers()).thenReturn(headers);
    when(headers.setAll(requestHeadersMapCaptor.capture())).thenReturn(headers);
    when(headers.set(eq(OkapiHeaders.REQUEST_ID), requestIdCaptor.capture())).thenReturn(headers);
    when(httpClientRequest.response()).thenReturn(succeededFuture(httpClientResponse));
    // Mock drainHandler method
    when(httpClientRequest.drainHandler(requestDrainHandlerCaptor.capture())).thenReturn(httpClientRequest);
    // Mock handler method
    HttpServerRequest httpServerRequest = routingContext.request();
    when(httpServerRequest.pause()).thenReturn(httpServerRequest);
    doReturn(true).when(httpClientRequest).writeQueueFull(); // Simulate write queue being full
    when(httpClientRequest.write(any(Buffer.class))).thenReturn(succeededFuture());
    when(httpServerRequest.handler(requestHandlerCaptor.capture())).thenReturn(httpServerRequest);
    // Mock endHandler method
    when(httpServerRequest.endHandler(requestEndHandlerCaptor.capture())).thenReturn(httpServerRequest);
  }

  private static RoutingContext routingContext(Consumer<RoutingContext> rcConsumer) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);

    when(routingContext.request()).thenReturn(request);
    when(request.method()).thenReturn(POST);
    when(request.path()).thenReturn(PATH);
    when(request.headers()).thenReturn(requestHeaders());
    when(request.params()).thenReturn(requestParams());

    rcConsumer.accept(routingContext);

    return routingContext;
  }

  private static void withHttpResponse(RoutingContext routingContext) {
    var response = mock(HttpServerResponse.class);
    when(routingContext.response()).thenReturn(response);
    when(response.setStatusCode(SC_OK)).thenReturn(response);
  }

  private static MultiMap requestHeaders() {
    return new HeadersMultiMap()
      .add(CONTENT_TYPE, APPLICATION_JSON)
      .add(USER_AGENT, "test-agent")
      .add(OkapiHeaders.TENANT, TestConstants.TENANT_ID)
      .add(OkapiHeaders.TOKEN, TestConstants.AUTH_TOKEN);
  }

  private static MultiMap responseHeaders() {
    return new HeadersMultiMap()
      .add(CONTENT_TYPE, APPLICATION_JSON)
      .add("tst-header", "tst-value");
  }

  private static MultiMap requestParams() {
    return new HeadersMultiMap()
      .add("query", "name==test")
      .add("offset", "10")
      .add("size", "50");
  }
}
