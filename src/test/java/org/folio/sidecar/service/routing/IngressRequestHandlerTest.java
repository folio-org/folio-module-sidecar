package org.folio.sidecar.service.routing;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.ForbiddenException;
import java.util.function.Consumer;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.filter.RequestFilterService;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.utils.RoutingUtils;
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

  @Mock private ErrorHandler errorHandler;
  @Mock private RequestFilterService requestFilterService;
  @Mock private RequestForwardingService requestForwardingService;

  @Spy private final ModuleProperties moduleProperties = moduleProperties();
  @Spy private final SidecarProperties sidecarProperties = sidecarProperties();

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(requestForwardingService, requestFilterService, errorHandler);
  }

  @Test
  void handle_positive() {
    var headers = new HeadersMultiMap();
    var routingPath = "/foo/entities";
    var routingContext = routingContext(rc -> when(rc.request().headers()).thenReturn(headers));
    var moduleBootstrapEndpoint = new ModuleBootstrapEndpoint(routingPath, "GET");
    var requestRoutingEntry = ScRoutingEntry.of(TestConstants.MODULE_ID, SIDECAR_URL, "foo", moduleBootstrapEndpoint);

    when(requestFilterService.filterIngressRequest(routingContext)).thenReturn(succeededFuture(routingContext));

    ingressRequestHandler.handle(routingContext, requestRoutingEntry);

    verify(sidecarProperties).getUrl();
    verify(moduleProperties).getUrl();
    verify(moduleProperties).getName();
    verify(requestForwardingService).forward(routingContext, TestConstants.MODULE_URL + routingPath);

    assertThat(headers).hasSize(2);
    assertThat(headers.get(OkapiHeaders.URL)).isEqualTo(SIDECAR_URL);
    assertThat(headers.get(OkapiHeaders.MODULE_ID)).isEqualTo(TestConstants.MODULE_ID);
    assertThat(headers.get(OkapiHeaders.PERMISSIONS_REQUIRED)).isNull();
  }

  @Test
  void handle_positive_selfRequest() {
    var headers = new HeadersMultiMap();
    var routingPath = "/foo/entities";
    var routingContext = routingContext(rc -> {
      when(rc.request().headers()).thenReturn(headers);
      when(rc.get(RoutingUtils.ADD_MODULE_NAME_TO_PATH_KEY)).thenReturn(true);
    });

    var moduleBootstrapEndpoint = new ModuleBootstrapEndpoint(routingPath, "GET");
    var requestRoutingEntry = ScRoutingEntry.of(TestConstants.MODULE_ID, SIDECAR_URL, "foo", moduleBootstrapEndpoint);

    when(requestFilterService.filterIngressRequest(routingContext)).thenReturn(succeededFuture(routingContext));

    ingressRequestHandler.handle(routingContext, requestRoutingEntry);

    verify(sidecarProperties).getUrl();
    verify(moduleProperties).getUrl();
    verify(moduleProperties).getName();

    var expectedPath = String.format("%s/%s%s", TestConstants.MODULE_URL, TestConstants.MODULE_NAME, routingPath);
    verify(requestForwardingService).forward(routingContext, expectedPath);

    assertThat(headers).hasSize(2);
    assertThat(headers.get(OkapiHeaders.URL)).isEqualTo(SIDECAR_URL);
    assertThat(headers.get(OkapiHeaders.MODULE_ID)).isEqualTo(TestConstants.MODULE_ID);
    assertThat(headers.get(OkapiHeaders.PERMISSIONS_REQUIRED)).isNull();
  }

  @Test
  void handle_negative_filterFailed() {
    var rc = routingContext(ctx -> {});
    var moduleBootstrapEndpoint = new ModuleBootstrapEndpoint("/foo/entities", "GET");
    var requestRoutingEntry = ScRoutingEntry.of(TestConstants.MODULE_ID, SIDECAR_URL, "foo", moduleBootstrapEndpoint);

    var error = new ForbiddenException("Access Denied");
    when(requestFilterService.filterIngressRequest(rc)).thenReturn(failedFuture(error));

    ingressRequestHandler.handle(rc, requestRoutingEntry);

    verify(errorHandler).sendErrorResponse(eq(rc), any(Throwable.class));
    verifyNoInteractions(requestForwardingService);
  }

  private static RoutingContext routingContext(Consumer<RoutingContext> modifier) {
    var routingContext = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/foo/entities");
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
