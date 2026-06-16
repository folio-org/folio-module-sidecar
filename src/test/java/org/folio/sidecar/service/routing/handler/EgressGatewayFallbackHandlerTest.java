package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.utils.RoutingUtils.SELF_REQUEST_KEY;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.RoutingContext;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EgressGatewayFallbackHandlerTest {

  @Mock ChainedHandler gatewayHandler;
  @Mock RoutingContext rc;

  private EgressGatewayFallbackHandler handler;

  @BeforeEach
  void setUp() {
    handler = new EgressGatewayFallbackHandler(gatewayHandler);
  }

  @Test
  void handle_selfRequest_forwardsToGateway() {
    when(rc.get(SELF_REQUEST_KEY)).thenReturn(true);
    when(gatewayHandler.handle(rc)).thenReturn(succeededFuture(true));

    var result = handler.handle(rc);

    assertThat(result.result()).isTrue();
    verify(gatewayHandler).handle(rc);
  }

  @Test
  void handle_nonSelfRequest_passesThroughWithoutForwarding() {
    var result = handler.handle(rc);

    assertThat(result.result()).isFalse();
    verify(gatewayHandler, never()).handle(rc);
  }
}
