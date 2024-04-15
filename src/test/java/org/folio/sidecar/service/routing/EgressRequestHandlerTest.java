package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.support.TestValues.scGatewayEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import jakarta.ws.rs.BadRequestException;
import java.util.stream.Stream;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.ServiceTokenProvider;
import org.folio.sidecar.service.SystemUserTokenProvider;
import org.folio.sidecar.service.filter.EgressRequestFilter;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
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
  @Mock private ErrorHandler errorHandler;
  @Mock private TestEgressFilter testEgressFilter;
  @Mock private RequestForwardingService requestForwardingService;
  @Mock private Instance<EgressRequestFilter> egressRequestFilters;
  @Mock private ServiceTokenProvider tokenProvider;
  @Mock private SystemUserTokenProvider systemUserService;

  @BeforeEach
  void setUp() {
    when(egressRequestFilters.stream()).thenReturn(Stream.of(testEgressFilter));
    egressRequestHandler = new EgressRequestHandler(
      errorHandler, requestForwardingService, egressRequestFilters, tokenProvider, systemUserService,
      TestConstants.MODULE_PROPERTIES);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(
      errorHandler, testEgressFilter, requestForwardingService, egressRequestFilters, requestHeaders,
      systemUserService);
  }

  @Test
  void handle_positive() {
    prepareHttpRequest(false);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(false);
    when(tokenProvider.getServiceToken(any(RoutingContext.class))).thenReturn(succeededFuture(SERVICE_TOKEN));
    when(systemUserService.getToken(anyString())).thenReturn(succeededFuture(USER_TOKEN));

    egressRequestHandler.handle(rc,
      ScRoutingEntry.of(TestConstants.MODULE_ID, egressModuleUrl, "foo", new ModuleBootstrapEndpoint()));

    verify(requestHeaders).set(OkapiHeaders.MODULE_ID, TestConstants.MODULE_ID);
    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestHeaders).set(OkapiHeaders.TOKEN, USER_TOKEN);
    verify(requestHeaders).remove(OkapiHeaders.USER_ID);
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl);
  }

  @Test
  void handle_positive_hasUserId_and_token() {
    prepareHttpRequest(false);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(true);
    when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(true);
    when(tokenProvider.getServiceToken(any(RoutingContext.class))).thenReturn(succeededFuture(SERVICE_TOKEN));

    egressRequestHandler.handle(rc,
      ScRoutingEntry.of(TestConstants.MODULE_ID, egressModuleUrl, "foo", new ModuleBootstrapEndpoint()));

    verify(requestHeaders).set(OkapiHeaders.MODULE_ID, TestConstants.MODULE_ID);
    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl);
  }

  @Test
  void handle_positive_sysUserNotSupported() {
    prepareHttpRequest(false);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(false);
    when(tokenProvider.getServiceToken(any(RoutingContext.class))).thenReturn(succeededFuture(SERVICE_TOKEN));
    when(systemUserService.getToken(anyString())).thenReturn(failedFuture("not found"));

    egressRequestHandler.handle(rc,
      ScRoutingEntry.of(TestConstants.MODULE_ID, egressModuleUrl, "foo", new ModuleBootstrapEndpoint()));

    var absoluteUrl = egressModuleUrl + fooEntitiesPath;
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl);
    verify(requestHeaders).set(OkapiHeaders.MODULE_ID, TestConstants.MODULE_ID);
    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestForwardingService).forwardEgress(rc, absoluteUrl);
  }

  @Test
  void handle_negative_sidecarLocationNotFound() {
    prepareHttpRequest(false);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));

    egressRequestHandler.handle(rc,
      ScRoutingEntry.of(TestConstants.MODULE_ID, null, "foo", new ModuleBootstrapEndpoint()));

    verify(errorHandler).sendErrorResponse(eq(rc), any(BadRequestException.class));
    verifyNoInteractions(requestForwardingService);
  }

  @Test
  void handle_positive_forwardUnknown() {
    prepareHttpRequest(false);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    when(request.headers()).thenReturn(requestHeaders);
    when(request.getHeader(OkapiHeaders.TENANT)).thenReturn(TestConstants.TENANT_NAME);
    when(requestHeaders.contains(OkapiHeaders.USER_ID)).thenReturn(false);
    when(tokenProvider.getServiceToken(any(RoutingContext.class))).thenReturn(succeededFuture(SERVICE_TOKEN));
    when(systemUserService.getToken(anyString())).thenReturn(succeededFuture(USER_TOKEN));

    egressRequestHandler.handle(rc, scGatewayEntry(TestConstants.GATEWAY_URL));

    verify(requestHeaders).set(OkapiHeaders.MODULE_ID, "NONE");
    verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SERVICE_TOKEN);
    verify(requestHeaders).set(OkapiHeaders.TOKEN, USER_TOKEN);
    verify(requestHeaders).remove(OkapiHeaders.USER_ID);
    verify(requestForwardingService).forwardToGateway(rc, TestConstants.GATEWAY_URL + fooEntitiesPath);
  }

  @Test
  void handle_negative_filterValidationFailed() {
    prepareHttpRequest(true);
    when(testEgressFilter.filter(rc)).thenReturn(succeededFuture(rc));
    egressRequestHandler.handle(rc,
      ScRoutingEntry.of(TestConstants.MODULE_ID, egressModuleUrl, "foo", new ModuleBootstrapEndpoint()));
    verifyNoInteractions(requestForwardingService);
  }

  private void prepareHttpRequest(boolean isEnded) {
    when(rc.request()).thenReturn(request);
    when(rc.response()).thenReturn(response);
    when(request.path()).thenReturn(fooEntitiesPath);
    when(request.method()).thenReturn(HttpMethod.GET);
    when(response.ended()).thenReturn(isEnded);
  }

  private abstract static class TestEgressFilter implements EgressRequestFilter {
  }
}
