package org.folio.sidecar.integration.okapi.filter;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OkapiHeaderValidationFilterTest {

  @Mock private RoutingContext routingContext;
  @Mock private HttpServerRequest request;
  @Mock private HttpServerResponse response;
  @Mock private MultiMap headers;

  private OkapiHeaderValidationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new OkapiHeaderValidationFilter();
    when(routingContext.request()).thenReturn(request);
    when(routingContext.response()).thenReturn(response);
    when(request.headers()).thenReturn(headers);
    when(response.putHeader(anyString(), anyString())).thenReturn(response);
  }

  @Test
  void filter_shouldAllowRequestWithNoOkapiHeaders() {
    when(headers.entries()).thenReturn(MultiMap.caseInsensitiveMultiMap().entries());

    filter.filter(routingContext);

    verify(routingContext).next();
    verify(response, never()).setStatusCode(eq(400));
  }

  @Test
  void filter_shouldAllowRequestWithUniqueOkapiHeaders() {
    MultiMap testHeaders = MultiMap.caseInsensitiveMultiMap()
      .add(OkapiHeaders.TENANT, "testtenant")
      .add(OkapiHeaders.TOKEN, "testtoken");
    when(headers.entries()).thenReturn(testHeaders.entries());

    filter.filter(routingContext);

    verify(routingContext).next();
    verify(response, never()).setStatusCode(eq(400));
  }

  @Test
  void filter_shouldRejectRequestWithDuplicateOkapiHeaders() {
    MultiMap testHeaders = MultiMap.caseInsensitiveMultiMap()
      .add(OkapiHeaders.TENANT, "testtenant1")
      .add(OkapiHeaders.TENANT, "testtenant2");
    when(headers.entries()).thenReturn(testHeaders.entries());
    when(response.setStatusCode(400)).thenReturn(response);

    filter.filter(routingContext);

    verify(response).setStatusCode(400);
    verify(response).putHeader("Content-Type", "text/plain");
    verify(response).end(anyString());
    verify(routingContext, never()).next();
  }

  @Test
  void filter_shouldRejectRequestWithDuplicateOkapiHeadersSameValue() {
    MultiMap testHeaders = MultiMap.caseInsensitiveMultiMap()
      .add(OkapiHeaders.TENANT, "testtenant")
      .add(OkapiHeaders.TENANT, "testtenant");
    when(headers.entries()).thenReturn(testHeaders.entries());
    when(response.setStatusCode(400)).thenReturn(response);

    filter.filter(routingContext);

    verify(response).setStatusCode(400);
    verify(response).putHeader("Content-Type", "text/plain");
    verify(response).end(anyString());
    verify(routingContext, never()).next();
  }
}