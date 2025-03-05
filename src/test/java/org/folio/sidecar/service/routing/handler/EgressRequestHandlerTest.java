package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestValues.scGatewayEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import java.util.stream.Stream;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.filter.EgressRequestFilter;
import org.folio.sidecar.service.filter.RequestFilterService;
import org.folio.sidecar.service.token.ServiceTokenProvider;
import org.folio.sidecar.service.token.SystemUserTokenProvider;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
@Disabled("Has to be revised due to extensive use of reactive code (Futures) in the class under test")
class EgressRequestHandlerTest {

  private static final String SERVICE_TOKEN = "test";
  private static final String USER_TOKEN = "sys-user";
  private final String egressModuleUrl = "http://mod-bar:8081";
  private final String fooEntitiesPath = "/foo/entities";
  private final String absoluteUrl = egressModuleUrl + fooEntitiesPath;
  private EgressRequestHandler egressRequestHandler;

  @Mock private RoutingContext rc;
  @Mock private HttpServerRequest request;
  @Mock private MultiMap requestHeaders;
  @Mock private HttpServerResponse response;
  @Mock private PathProcessor pathProcessor;
  @Mock private TestEgressFilter testEgressFilter;
  @Mock private RequestForwardingService requestForwardingService;
  @Mock private Instance<EgressRequestFilter> egressRequestFilters;
  @Mock private ServiceTokenProvider tokenProvider;
  @Mock private SystemUserTokenProvider systemUserService;
  @Mock private RequestFilterService requestFilterService;

  @BeforeEach
  void setUp() {
    when(egressRequestFilters.stream()).thenReturn(Stream.of(testEgressFilter));
    egressRequestHandler = new EgressRequestHandler(
      pathProcessor, requestFilterService, requestForwardingService, tokenProvider, systemUserService);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(testEgressFilter, requestForwardingService,
      egressRequestFilters, requestHeaders, systemUserService);
  }

  @Test
  void handle_positive() {
    prepareHttpRequest(false);
    when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader(OkapiHeaders.REQUEST_ID)).thenReturn("reqId");
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(false);
    when(tokenProvider.getTokenSync(any())).thenReturn(SERVICE_TOKEN);
    when(systemUserService.getTokenSync(rc)).thenReturn(USER_TOKEN);

    egressRequestHandler.handle(routingEntry(), rc);

    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestHeaders).set(OkapiHeaders.TOKEN, USER_TOKEN);
    verify(requestHeaders).remove(OkapiHeaders.USER_ID);
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl, any());
  }

  @Test
  void handle_positive_hasUserId_and_token() {
    prepareHttpRequest(false);
    when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader("X-Okapi-Request-Id")).thenReturn("reqId");
    when(request.getHeader("X-Okapi-Token")).thenReturn("org/folio/sidecar/service/token");
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(true);
    when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(true);
    when(tokenProvider.getTokenSync(any())).thenReturn(SERVICE_TOKEN);

    egressRequestHandler.handle(routingEntry(), rc);

    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestHeaders).contains(OkapiHeaders.USER_ID);
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl, any());
  }

  @Test
  void handle_positive_okapiToken_null() {
    prepareHttpRequest(false);
    when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader("X-Okapi-Request-Id")).thenReturn("reqId");
    when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(true);
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(true);
    when(tokenProvider.getTokenSync(any())).thenReturn(SERVICE_TOKEN);
    when(systemUserService.getTokenSync(rc)).thenReturn(USER_TOKEN);

    egressRequestHandler.handle(routingEntry(), rc);

    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestHeaders).set(OkapiHeaders.TOKEN, USER_TOKEN);
    verify(requestHeaders).remove(OkapiHeaders.USER_ID);
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl, any());
  }

  @Test
  void handle_positive_sysUserNotSupported() {
    prepareHttpRequest(false);
    when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader(OkapiHeaders.REQUEST_ID)).thenReturn("reqId");
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(false);
    when(tokenProvider.getTokenSync(any())).thenReturn(SERVICE_TOKEN);
    when(systemUserService.getTokenSync(rc)).thenReturn(null);

    egressRequestHandler.handle(routingEntry(), rc);

    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl, any());
  }

  @Test
  void handle_negative_sidecarLocationNotFound() {
    prepareHttpRequest(false);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));

    var routingEntry = ScRoutingEntry.of(MODULE_ID, null, "foo", new ModuleBootstrapEndpoint());
    egressRequestHandler.handle(routingEntry, rc);

    verifyNoInteractions(requestForwardingService);
  }

  @Test
  void handle_positive_forwardUnknown() {
    prepareHttpRequest(false);
    when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader(OkapiHeaders.REQUEST_ID)).thenReturn("reqId");
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(false);
    when(tokenProvider.getTokenSync(any())).thenReturn(SERVICE_TOKEN);
    when(systemUserService.getTokenSync(rc)).thenReturn(USER_TOKEN);

    egressRequestHandler.handle(scGatewayEntry(TestConstants.GATEWAY_URL), rc);

    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestHeaders).set(OkapiHeaders.TOKEN, USER_TOKEN);
    verify(requestHeaders).remove(OkapiHeaders.USER_ID);
    verify(requestForwardingService).forwardToGateway(rc, TestConstants.GATEWAY_URL + fooEntitiesPath, any());
  }

  @Test
  void handle_negative_filterValidationFailed() {
    prepareHttpRequest(true);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    egressRequestHandler.handle(routingEntry(), rc);
    verifyNoInteractions(requestForwardingService);
  }

  private void prepareHttpRequest(boolean isEnded) {
    when(rc.request()).thenReturn(request);
    when(rc.response()).thenReturn(response);
    when(request.path()).thenReturn(fooEntitiesPath);
    when(request.method()).thenReturn(HttpMethod.GET);
    when(response.ended()).thenReturn(isEnded);
  }

  private static ScRoutingEntry routingEntry() {
    return ScRoutingEntry.of(MODULE_ID, "http://mod-bar:8081", "foo", new ModuleBootstrapEndpoint());
  }

  private abstract static class TestEgressFilter implements EgressRequestFilter {}
}
