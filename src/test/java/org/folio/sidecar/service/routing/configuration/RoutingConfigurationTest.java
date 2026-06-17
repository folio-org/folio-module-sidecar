package org.folio.sidecar.service.routing.configuration;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.utils.RoutingUtils.SELF_REQUEST_KEY;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.List;
import org.folio.sidecar.service.routing.handler.ChainedHandler;
import org.folio.sidecar.service.routing.handler.EgressGatewayFallbackHandler;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies the handler-chain assembly in {@link RoutingConfiguration#chainedHandler} for the egress gateway
 * fallback: an unmatched OUTBOUND (self) request must reach the fallback and be forwarded to the gateway, while
 * an unmatched non-self (inbound) request must fall through to the not-found handler. This guards the chain
 * ORDER and the {@code isSelfRequest} gate together — the exact regression surface an HTTP-level integration
 * test cannot cover, because {@code isSelfRequest} depends on the per-JVM sidecar signature that an external
 * caller cannot reproduce.
 */
@UnitTest
@ExtendWith(MockitoExtension.class)
class RoutingConfigurationTest {

  @Mock RoutingContext rc;
  @Mock Instance<ChainedHandler> moduleEntitlementHandler;
  @Mock Instance<ChainedHandler> dynamicEgressHandler;
  @Mock Instance<ChainedHandler> gatewayEgressHandler;
  @Mock Instance<ChainedHandler> egressGatewayFallbackHandler;

  private final List<String> invoked = new ArrayList<>();

  @Test
  void chainedHandler_selfRequestUnmatchedEgress_forwardedToGatewayFallback() {
    var chain = buildChain();
    when(rc.get(SELF_REQUEST_KEY)).thenReturn(true);

    var handled = chain.handle(rc).result();

    assertThat(handled).isTrue();
    assertThat(invoked).containsExactly("gateway");
  }

  @Test
  void chainedHandler_nonSelfRequestUnmatchedEgress_fallsThroughToNotFound() {
    var chain = buildChain();
    when(rc.get(SELF_REQUEST_KEY)).thenReturn(false);

    var handled = chain.handle(rc).result();

    assertThat(handled).isTrue();
    assertThat(invoked).containsExactly("notFound");
  }

  @Test
  void chainedHandler_fallbackDisabled_unmatchedEgress_fallsThroughToNotFound() {
    // routing.egress.fallback-to-gateway.enabled=false -> the fallback handler is not resolvable and is omitted from
    // the chain, so an unmatched (self or not) request reaches not-found (404) instead of being forwarded.
    var chain = buildChainFallbackDisabled();

    var handled = chain.handle(rc).result();

    assertThat(handled).isTrue();
    assertThat(invoked).containsExactly("notFound");
  }

  private ChainedHandler buildChainFallbackDisabled() {
    when(moduleEntitlementHandler.isResolvable()).thenReturn(false);
    when(dynamicEgressHandler.isResolvable()).thenReturn(false);
    when(gatewayEgressHandler.isResolvable()).thenReturn(false);
    when(egressGatewayFallbackHandler.isResolvable()).thenReturn(false);

    ChainedHandler ingress = context -> succeededFuture(false);
    ChainedHandler egress = context -> succeededFuture(false);
    ChainedHandler notFound = recording("notFound");

    return new RoutingConfiguration().chainedHandler(moduleEntitlementHandler, ingress, egress,
      dynamicEgressHandler, gatewayEgressHandler, egressGatewayFallbackHandler, notFound);
  }

  private ChainedHandler buildChain() {
    when(moduleEntitlementHandler.isResolvable()).thenReturn(false);
    when(dynamicEgressHandler.isResolvable()).thenReturn(false);
    when(gatewayEgressHandler.isResolvable()).thenReturn(false);

    when(egressGatewayFallbackHandler.isResolvable()).thenReturn(true);
    when(egressGatewayFallbackHandler.get()).thenReturn(new EgressGatewayFallbackHandler(recording("gateway")));

    ChainedHandler ingress = context -> succeededFuture(false);
    ChainedHandler egress = context -> succeededFuture(false);
    ChainedHandler notFound = recording("notFound");

    return new RoutingConfiguration().chainedHandler(moduleEntitlementHandler, ingress, egress,
      dynamicEgressHandler, gatewayEgressHandler, egressGatewayFallbackHandler, notFound);
  }

  private ChainedHandler recording(String name) {
    return context -> {
      invoked.add(name);
      return succeededFuture(true);
    };
  }
}
