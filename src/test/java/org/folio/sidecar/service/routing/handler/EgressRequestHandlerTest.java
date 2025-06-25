package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.support.TestConstants.GATEWAY_URL;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.SYS_TOKEN;
import static org.folio.sidecar.support.TestConstants.SYS_USER_TOKEN;
import static org.folio.sidecar.support.TestConstants.USER_TOKEN;
import static org.folio.sidecar.support.TestValues.scGatewayEntry;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.BadRequestException;
import java.util.Optional;
import java.util.function.Consumer;
import org.folio.sidecar.configuration.properties.ModuleProperties;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EgressRequestHandlerTest {

  private final String egressModuleUrl = "http://mod-bar:8081";
  private final String fooEntitiesPath = "/foo/entities";
  private final String absoluteUrl = egressModuleUrl + fooEntitiesPath;

  @Mock private RoutingContext rc;
  @Mock private HttpServerRequest request;
  @Mock private MultiMap requestHeaders;
  @Mock private PathProcessor pathProcessor;
  @Mock private RequestForwardingService requestForwardingService;
  @Mock private Instance<EgressRequestFilter> egressRequestFilters;
  @Mock private ServiceTokenProvider serviceTokenProvider;
  @Mock private SystemUserTokenProvider systemUserTokenProvider;
  @Mock private RequestFilterService requestFilterService;
  @Mock private ModuleProperties moduleProperties;

  private EgressRequestHandler egressRequestHandler;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(requestForwardingService, egressRequestFilters, requestHeaders, systemUserTokenProvider,
      pathProcessor);
  }

  private void prepareHttpRequest(Consumer<HttpServerRequest> customizer) {
    when(rc.request()).thenReturn(request);
    when(request.uri()).thenReturn(fooEntitiesPath);
    when(request.method()).thenReturn(HttpMethod.GET);
    when(request.getHeader(REQUEST_ID)).thenReturn(TestConstants.REQUEST_ID);

    customizer.accept(request);
  }

  private static ScRoutingEntry routingEntry() {
    return ScRoutingEntry.of(MODULE_ID, "http://mod-bar:8081", "foo", new ModuleBootstrapEndpoint());
  }

  @Nested
  class WithDefaults {

    @BeforeEach
    void setUp() {
      egressRequestHandler = new EgressRequestHandler(pathProcessor, requestFilterService, requestForwardingService,
        serviceTokenProvider, systemUserTokenProvider, moduleProperties, false);
    }

    @Test
    void handle_positive() {
      prepareHttpRequest(req -> when(req.path()).thenReturn(fooEntitiesPath));

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(succeededFuture(SYS_TOKEN));

      when(request.headers()).thenReturn(requestHeaders);
      when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(false);
      when(systemUserTokenProvider.getToken(rc)).thenReturn(succeededFuture(Optional.of(SYS_USER_TOKEN)));

      when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
      when(requestForwardingService.forwardEgress(rc, absoluteUrl)).thenReturn(succeededFuture());

      var rf = egressRequestHandler.handle(routingEntry(), rc);

      assertThat(rf.succeeded()).isTrue();

      verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SYS_TOKEN);
      verify(requestHeaders).set(OkapiHeaders.TOKEN, SYS_USER_TOKEN);
    }

    @Test
    void handle_positive_hasUserToken() {
      prepareHttpRequest(req -> when(req.path()).thenReturn(fooEntitiesPath));

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(succeededFuture(SYS_TOKEN));

      when(request.headers()).thenReturn(requestHeaders);
      when(request.getHeader(OkapiHeaders.TOKEN)).thenReturn(USER_TOKEN);
      when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(true);

      when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
      when(requestForwardingService.forwardEgress(rc, absoluteUrl)).thenReturn(succeededFuture());

      egressRequestHandler.handle(routingEntry(), rc);

      verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SYS_TOKEN);
      verify(requestHeaders, never()).set(eq(OkapiHeaders.TOKEN), anyString());
    }

    @Test
    void handle_negative_sysUserTokenIsEmpty() {
      prepareHttpRequest(req -> {});

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(succeededFuture(SYS_TOKEN));

      when(request.headers()).thenReturn(requestHeaders);
      when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(false);
      when(systemUserTokenProvider.getToken(rc)).thenReturn(succeededFuture(Optional.empty()));
      when(moduleProperties.getId()).thenReturn(MODULE_ID);

      var rf = egressRequestHandler.handle(routingEntry(), rc);

      assertThat(rf.failed()).isTrue();
      assertThat(rf.cause())
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("System user token is required");

      verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SYS_TOKEN);
    }

    @Test
    void handle_negative_sysUserTokenGetFailed() {
      prepareHttpRequest(req -> {});

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(succeededFuture(SYS_TOKEN));

      when(request.headers()).thenReturn(requestHeaders);
      when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(false);
      when(systemUserTokenProvider.getToken(rc)).thenReturn(failedFuture("System user token is not found"));

      var rf = egressRequestHandler.handle(routingEntry(), rc);

      assertThat(rf.failed()).isTrue();
      assertThat(rf.cause()).hasMessage("System user token is not found");

      verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SYS_TOKEN);
      verify(requestHeaders, never()).set(eq(OkapiHeaders.TOKEN), anyString());
    }

    @Test
    void handle_positive_forwardToGateway() {
      prepareHttpRequest(req -> when(req.path()).thenReturn(fooEntitiesPath));

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(succeededFuture(SYS_TOKEN));

      when(request.headers()).thenReturn(requestHeaders);
      when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(false);
      when(systemUserTokenProvider.getToken(rc)).thenReturn(succeededFuture(Optional.of(SYS_USER_TOKEN)));

      when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
      when(requestForwardingService.forwardToGateway(rc, GATEWAY_URL + fooEntitiesPath)).thenReturn(succeededFuture());

      var rf = egressRequestHandler.handle(scGatewayEntry(GATEWAY_URL), rc);

      assertThat(rf.succeeded()).isTrue();

      verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SYS_TOKEN);
      verify(requestHeaders).set(OkapiHeaders.TOKEN, SYS_USER_TOKEN);
    }

    @Test
    void handle_negative_sysTokenGetFailed() {
      prepareHttpRequest(req -> {});

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(failedFuture("Service token is not found"));

      var rf = egressRequestHandler.handle(routingEntry(), rc);

      assertThat(rf.failed()).isTrue();
      assertThat(rf.cause()).hasMessage("Service token is not found");
    }

    @Test
    void handle_negative_moduleLocationNotFound() {
      prepareHttpRequest(req -> {});

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));

      var routingEntry = ScRoutingEntry.of(MODULE_ID, null, "foo", new ModuleBootstrapEndpoint());
      var rf = egressRequestHandler.handle(routingEntry, rc);

      assertThat(rf.failed()).isTrue();
      assertThat(rf.cause()).hasMessage("Module location is not found for moduleId: %s", MODULE_ID);
    }

    @Test
    void handle_negative_filterValidationFailed() {
      prepareHttpRequest(req -> {});

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(failedFuture("Filter validation failed"));

      var rf = egressRequestHandler.handle(routingEntry(), rc);

      assertThat(rf.failed()).isTrue();
      assertThat(rf.cause()).hasMessage("Filter validation failed");
    }
  }

  @Nested
  class WhenIgnoreGettingSystemUserTokenError {

    @BeforeEach
    void setUp() {
      egressRequestHandler = new EgressRequestHandler(pathProcessor, requestFilterService, requestForwardingService,
        serviceTokenProvider, systemUserTokenProvider, moduleProperties, true);
    }

    @Test
    void handle_positive_sysUserTokenIsEmpty() {
      prepareHttpRequest(req -> when(req.path()).thenReturn(fooEntitiesPath));

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(succeededFuture(SYS_TOKEN));

      when(request.headers()).thenReturn(requestHeaders);
      when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(false);
      when(systemUserTokenProvider.getToken(rc)).thenReturn(succeededFuture(Optional.empty()));
      when(moduleProperties.getId()).thenReturn(MODULE_ID);

      when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
      when(requestForwardingService.forwardEgress(rc, absoluteUrl)).thenReturn(succeededFuture());

      var rf = egressRequestHandler.handle(routingEntry(), rc);

      assertThat(rf.succeeded()).isTrue();

      verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SYS_TOKEN);
      verify(requestHeaders, never()).set(eq(OkapiHeaders.TOKEN), anyString());
    }

    @Test
    void handle_positive_sysUserTokenGetFailed() {
      prepareHttpRequest(req -> when(req.path()).thenReturn(fooEntitiesPath));

      when(requestFilterService.filterEgressRequest(rc)).thenReturn(succeededFuture(rc));
      when(serviceTokenProvider.getToken(rc)).thenReturn(succeededFuture(SYS_TOKEN));

      when(request.headers()).thenReturn(requestHeaders);
      when(requestHeaders.contains(OkapiHeaders.TOKEN)).thenReturn(false);
      when(systemUserTokenProvider.getToken(rc)).thenReturn(failedFuture("System user token is not found"));

      when(pathProcessor.cleanIngressRequestPath(fooEntitiesPath)).thenReturn(fooEntitiesPath);
      when(requestForwardingService.forwardEgress(rc, absoluteUrl)).thenReturn(succeededFuture());

      var rf = egressRequestHandler.handle(routingEntry(), rc);

      assertThat(rf.succeeded()).isTrue();

      verify(requestHeaders).set(OkapiHeaders.SYSTEM_TOKEN, SYS_TOKEN);
      verify(requestHeaders, never()).set(eq(OkapiHeaders.TOKEN), anyString());
    }
  }
}
