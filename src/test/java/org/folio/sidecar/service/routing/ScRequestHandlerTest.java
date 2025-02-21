package org.folio.sidecar.service.routing;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.vertx.ext.web.RoutingContext;
import org.folio.sidecar.service.ErrorHandler;
import org.folio.sidecar.service.routing.handler.ChainedHandler;
import org.folio.sidecar.service.routing.handler.ScRequestHandler;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScRequestHandlerTest {

  @InjectMocks private ScRequestHandler requestHandler;
  @Mock private ErrorHandler errorHandler;
  @Mock private ChainedHandler chainedHandler;
  @Mock private RoutingContext rc;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(errorHandler, chainedHandler, rc);
  }

  @Test
  void handle_positive() {
    requestHandler.handle(rc);

    verify(rc).put(eq("rt"), anyLong());
    verify(chainedHandler).handle(rc);
  }

  @Test
  void handle_negative_exceptionThrown() {
    RuntimeException exception = new RuntimeException("error");
    doThrow(exception).when(chainedHandler).handle(rc);

    requestHandler.handle(rc);

    verify(rc).put(eq("rt"), anyLong());
    verify(chainedHandler).handle(rc);
    verify(errorHandler).sendErrorResponse(rc, exception);
  }
}
