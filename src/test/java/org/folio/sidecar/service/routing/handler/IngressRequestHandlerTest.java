package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.security.ForbiddenException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Consumer;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.service.filter.RequestFilterService;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IngressRequestHandlerTest {

  private static final String SIDECAR_URL = "http://localhost:18081";

  @InjectMocks private IngressRequestHandler ingressRequestHandler;

  @Mock private PathProcessor pathProcessor;
  @Mock private RequestFilterService requestFilterService;
  @Mock private RequestForwardingService requestForwardingService;

  @Spy private final ModuleProperties moduleProperties = moduleProperties();
  @Spy private final SidecarProperties sidecarProperties = sidecarProperties();

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(requestForwardingService, requestFilterService, sidecarProperties);
  }

  @Test
  void handle_positive() {
    var headers = new HeadersMultiMap();
    var routingPath = "/foo/entities";
    var routingContext = routingContext(rc -> {
      when(rc.request().path()).thenReturn("/foo/entities");
      when(rc.request().headers()).thenReturn(headers);
    });
    var moduleBootstrapEndpoint = new ModuleBootstrapEndpoint(routingPath, "GET");

    when(pathProcessor.getModulePath(routingPath)).thenReturn(routingPath);
    when(requestFilterService.filterIngressRequest(routingContext)).thenReturn(succeededFuture(routingContext));
    when(requestForwardingService.forwardIngress(routingContext, TestConstants.MODULE_URL + routingPath))
      .thenReturn(succeededFuture());

    var requestRoutingEntry = ScRoutingEntry.of(TestConstants.MODULE_ID, SIDECAR_URL, "foo", moduleBootstrapEndpoint);

    ingressRequestHandler.handle(requestRoutingEntry, routingContext);

    verify(sidecarProperties).getUrl();
    verify(moduleProperties).getUrl();

    assertThat(headers).hasSize(1);
    assertThat(headers.get(OkapiHeaders.URL)).isEqualTo(SIDECAR_URL);
    assertThat(headers.get(OkapiHeaders.PERMISSIONS_REQUIRED)).isNull();
  }

  @Test
  void handle_negative_filterFailed() {
    var rc = routingContext(ctx -> {});
    var moduleBootstrapEndpoint = new ModuleBootstrapEndpoint("/foo/entities", "GET");
    var requestRoutingEntry = ScRoutingEntry.of(TestConstants.MODULE_ID, SIDECAR_URL, "foo", moduleBootstrapEndpoint);

    var error = new ForbiddenException("Access Denied");
    when(requestFilterService.filterIngressRequest(rc)).thenReturn(failedFuture(error));

    var result = ingressRequestHandler.handle(requestRoutingEntry, rc);

    verifyNoInteractions(requestForwardingService);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause()).isEqualTo(error);
  }

  private static RoutingContext routingContext(Consumer<RoutingContext> modifier) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(request);
    when(request.uri()).thenReturn("/foo/entities");
    when(request.method()).thenReturn(HttpMethod.GET);

    modifier.accept(routingContext);

    return routingContext;
  }

  private static SidecarProperties sidecarProperties() {
    var sidecarProperties = new SidecarProperties();
    sidecarProperties.setUrl(SIDECAR_URL);
    return sidecarProperties;
  }

  private static ModuleProperties moduleProperties() {
    var moduleProperties = new ModuleProperties();
    moduleProperties.setId(TestConstants.MODULE_ID);
    moduleProperties.setName(TestConstants.MODULE_NAME);
    moduleProperties.setUrl(TestConstants.MODULE_URL);
    return moduleProperties;
  }
}
