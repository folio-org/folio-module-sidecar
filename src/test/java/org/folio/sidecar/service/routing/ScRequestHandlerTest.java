package org.folio.sidecar.service.routing;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.NotFoundException;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.routing.handler.EgressRequestHandler;
import org.folio.sidecar.service.routing.handler.IngressRequestHandler;
import org.folio.sidecar.service.routing.handler.ScRequestHandler;
import org.folio.sidecar.service.routing.lookup.RoutingLookupUtils;
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
class ScRequestHandlerTest {

  private ScRequestHandler requestHandler;

  @Mock private ErrorHandler errorHandler;
  @Mock private EgressRequestHandler egressRequestHandler;
  @Mock private IngressRequestHandler ingressRequestHandler;
  @Mock private RoutingLookupUtils requestMatchingService;
  @Mock private RoutingContext rc;

  @BeforeEach
  void setUp() {
    var moduleDescriptor = new ModuleBootstrapDiscovery();
    moduleDescriptor.setModuleId(TestConstants.MODULE_ID);
    requestHandler = new ScRequestHandler(ingressRequestHandler, egressRequestHandler, requestMatchingService,
      errorHandler);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(errorHandler, egressRequestHandler, ingressRequestHandler, requestMatchingService);
  }

  @Test
  void handle_positive_ingressRequest() {
    var rre = ScRoutingEntry.of(TestConstants.MODULE_ID, TestConstants.MODULE_URL, "foo", routingEntry());
    when(requestMatchingService.lookupForIngressRequest(rc)).thenReturn(of(rre));

    requestHandler.handle(rc);

    verify(ingressRequestHandler).handle(rre, rc);
  }

  @Test
  void handle_positive_egressRequest() {
    var rre = ScRoutingEntry.of(TestConstants.MODULE_ID, TestConstants.MODULE_URL, "foo", routingEntry());
    when(requestMatchingService.lookupForIngressRequest(rc)).thenReturn(empty());
    when(requestMatchingService.lookupForEgressRequest(rc)).thenReturn(of(rre));

    requestHandler.handle(rc);

    verify(egressRequestHandler).handle(rre, rc);
  }

  @Test
  void handle_negative_egressRequestNotFound() {
    var request = mock(HttpServerRequest.class);
    when(requestMatchingService.lookupForIngressRequest(rc)).thenReturn(empty());
    when(requestMatchingService.lookupForEgressRequest(rc)).thenReturn(empty());
    when(rc.request()).thenReturn(request);
    when(request.method()).thenReturn(HttpMethod.GET);
    when(request.path()).thenReturn("/baz/entities");

    requestHandler.handle(rc);

    verifyNoInteractions(egressRequestHandler);
    verify(errorHandler).sendErrorResponse(eq(rc), any(NotFoundException.class));
  }

  @Test
  void handle_negative_failedToLoadRequiredInterfaces() {
    var error = new RuntimeException();

    when(requestMatchingService.lookupForIngressRequest(rc)).thenReturn(empty());
    when(requestMatchingService.lookupForEgressRequest(rc)).thenThrow(error);

    requestHandler.handle(rc);

    verify(errorHandler).sendErrorResponse(eq(rc), any(RuntimeException.class));
    verifyNoInteractions(egressRequestHandler);
  }

  private static ModuleBootstrapEndpoint routingEntry() {
    var routingEntry = new ModuleBootstrapEndpoint();
    routingEntry.setPathPattern("/foo/entities");
    return routingEntry;
  }
}
