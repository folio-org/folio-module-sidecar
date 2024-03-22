package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.TimeoutException;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.ws.rs.InternalServerErrorException;
import java.util.function.Consumer;
import org.folio.sidecar.configuration.properties.WebClientProperties;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.SidecarSignatureService;
import org.folio.sidecar.service.TransactionLogHandler;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RequestForwardingServiceTest {

  public static final long TIMEOUT = 5000L;
  private final String absoluteUrl = TestConstants.MODULE_URL + "/foo/entities";

  @InjectMocks private RequestForwardingService service;
  @Mock private WebClient webClient;
  @Mock private ErrorHandler errorHandler;
  @Mock private HttpRequest<Buffer> httpRequest;
  @Mock private HttpResponse<Buffer> httpResponse;
  @Mock private Buffer buffer;
  @Mock private MultiMap headers;
  @Mock private SidecarSignatureService sidecarSignatureService;
  @Mock private WebClientProperties webClientProperties;
  @Mock private TransactionLogHandler transactionLogHandler;

  @Captor private ArgumentCaptor<MultiMap> requestHeadersMapCaptor;
  @Captor private ArgumentCaptor<MultiMap> responseHeadersMapCaptor;
  @Captor private ArgumentCaptor<String> queryParamCaptor;
  @Captor private ArgumentCaptor<String> headersCaptor;
  @Captor private ArgumentCaptor<String> requestIdCaptor;

  @Test
  void forward_positive() {
    var routingContext = routingContext(RequestForwardingServiceTest::withHttpResponse);

    when(webClient.requestAbs(GET, absoluteUrl)).thenReturn(httpRequest);
    prepareHttpRequestMocks(routingContext);
    prepareHttpResponseMocks(buffer);

    var response = routingContext.response();
    when(response.headers()).thenReturn(headers);
    when(headers.addAll(responseHeadersMapCaptor.capture())).thenReturn(headers);
    when(response.end(buffer)).thenReturn(succeededFuture());

    service.forward(routingContext, absoluteUrl);

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
    assertThat(queryParamCaptor.getAllValues()).containsExactly("query", "name==test", "offset", "10", "size", "50");
    assertThat(requestIdCaptor.getValue()).isNotEmpty().matches("\\d{6}/foo");

    verify(sidecarSignatureService).removeSignature(any(HttpServerResponse.class));
  }

  @Test
  void forward_positive_nullBodyBuffer() {
    var routingContext = routingContext(RequestForwardingServiceTest::withHttpResponse);

    when(webClient.requestAbs(GET, absoluteUrl)).thenReturn(httpRequest);
    prepareHttpRequestMocks(routingContext);
    prepareHttpResponseMocks(null);

    var response = routingContext.response();
    when(response.headers()).thenReturn(headers);
    when(headers.addAll(responseHeadersMapCaptor.capture())).thenReturn(headers);
    when(response.end()).thenReturn(succeededFuture());

    service.forward(routingContext, absoluteUrl);

    var capturedRequestHeaders = requestHeadersMapCaptor.getValue();
    assertThat(capturedRequestHeaders).hasSize(3);
    assertThat(capturedRequestHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TENANT)).isEqualTo(TestConstants.TENANT_ID);
    assertThat(capturedRequestHeaders.get(OkapiHeaders.TOKEN)).isEqualTo(TestConstants.AUTH_TOKEN);

    var responseHeaders = responseHeadersMapCaptor.getValue();
    assertThat(responseHeaders).hasSize(2);
    assertThat(responseHeaders.get("tst-header")).isEqualTo("tst-value");
    assertThat(responseHeaders.get(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);

    assertThat(queryParamCaptor.getAllValues()).containsExactly("query", "name==test", "offset", "10", "size", "50");
    assertThat(requestIdCaptor.getValue()).isNotEmpty().matches("\\d{6}/foo");
    assertThat(responseHeaders.get(TestConstants.SIDECAR_SIGNATURE_HEADER)).isNull();

    verify(sidecarSignatureService).removeSignature(any(HttpServerResponse.class));
  }

  @Test
  void forward_negative() {
    var routingContext = routingContext(rc -> {});
    var error = new TimeoutException();

    when(webClient.requestAbs(GET, absoluteUrl)).thenReturn(httpRequest);
    when(webClientProperties.getTimeout()).thenReturn(TIMEOUT);
    when(httpRequest.timeout(TIMEOUT)).thenReturn(httpRequest);
    when(httpRequest.putHeaders(any(MultiMap.class))).thenReturn(httpRequest);
    when(httpRequest.addQueryParam(anyString(), anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(OkapiHeaders.REQUEST_ID), anyString())).thenReturn(httpRequest);
    when(httpRequest.sendStream(routingContext.request())).thenReturn(failedFuture(error));

    service.forward(routingContext, absoluteUrl);

    verify(errorHandler).sendErrorResponse(eq(routingContext), any(InternalServerErrorException.class));
  }

  private void prepareHttpResponseMocks(Buffer buffer) {
    when(httpResponse.headers()).thenReturn(responseHeaders());
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpResponse.bodyAsBuffer()).thenReturn(buffer);
  }

  private void prepareHttpRequestMocks(RoutingContext routingContext) {
    when(webClientProperties.getTimeout()).thenReturn(TIMEOUT);
    when(httpRequest.timeout(TIMEOUT)).thenReturn(httpRequest);
    when(httpRequest.putHeaders(requestHeadersMapCaptor.capture())).thenReturn(httpRequest);
    when(httpRequest.addQueryParam(queryParamCaptor.capture(), queryParamCaptor.capture())).thenReturn(httpRequest);
    when(httpRequest.putHeader(eq(OkapiHeaders.REQUEST_ID), requestIdCaptor.capture())).thenReturn(httpRequest);
    when(httpRequest.sendStream(routingContext.request())).thenReturn(succeededFuture(httpResponse));
  }

  private static RoutingContext routingContext(Consumer<RoutingContext> rcConsumer) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);

    when(routingContext.request()).thenReturn(request);
    when(request.method()).thenReturn(GET);
    when(request.path()).thenReturn("/foo/entities");
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
