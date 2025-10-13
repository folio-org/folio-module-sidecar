package org.folio.sidecar.service.routing.handler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Set;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TraceHeadersHandlerTest {

  @Mock private Handler<RoutingContext> routingContextHandler;
  @Mock private RoutingContext routingContext;
  @Mock private HttpServerRequest request;

  @Test
  void handle_positive_pathMatchedAndLoggingEnabled() {
    var paths = Set.of("/test");
    var headers = new HeadersMultiMap();

    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/test/endpoint");
    when(request.method()).thenReturn(HttpMethod.GET);
    when(request.uri()).thenReturn("/test/endpoint?param=value");
    when(request.headers()).thenReturn(headers);

    var handler = new TraceHeadersHandler(routingContextHandler, paths);
    handler.handle(routingContext);

    verify(routingContextHandler).handle(routingContext);
  }

  @Test
  void handle_positive_pathNotMatched() {
    var paths = Set.of("/api");
    var handler = new TraceHeadersHandler(routingContextHandler, paths);

    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/other/endpoint");

    handler.handle(routingContext);

    verify(routingContextHandler).handle(routingContext);
    verify(routingContext).request();
    verify(request).path();
  }

  @Test
  void handle_positive_emptyPaths() {
    var headers = new HeadersMultiMap();

    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/any/path");
    when(request.method()).thenReturn(HttpMethod.POST);
    when(request.uri()).thenReturn("/any/path");
    when(request.headers()).thenReturn(headers);

    var handler = new TraceHeadersHandler(routingContextHandler, List.of());
    handler.handle(routingContext);

    verify(routingContextHandler).handle(routingContext);
  }

  @Test
  void handle_positive_nullPath() {
    var paths = Set.of("/test");
    var handler = new TraceHeadersHandler(routingContextHandler, paths);

    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn(null);

    handler.handle(routingContext);

    verify(routingContextHandler).handle(routingContext);
    verify(routingContext).request();
    verify(request).path();
  }

  @Test
  void handle_positive_caseInsensitiveMatch() {
    var paths = Set.of("/Test");
    var headers = new HeadersMultiMap();

    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/test/endpoint");
    when(request.method()).thenReturn(HttpMethod.GET);
    when(request.uri()).thenReturn("/test/endpoint");
    when(request.headers()).thenReturn(headers);

    var handler = new TraceHeadersHandler(routingContextHandler, paths);
    handler.handle(routingContext);

    verify(routingContextHandler).handle(routingContext);
  }
}
