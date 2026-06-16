package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.sidecar.utils.RoutingUtils.isSelfRequest;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

/**
 * Last-resort egress fallback: forwards an unmatched OUTBOUND (self/egress) request to the gateway, while leaving
 * unmatched inbound (ingress) requests to fall through to the not-found handler. This preserves egress routing for
 * tenants without a scoped egress table (e.g. not yet refreshed, out of scope, or an older mgr-applications without
 * the bootstrap endpoint) without turning every genuinely-unknown inbound route into a gateway forward.
 *
 * <p>Direction is taken from {@code SELF_REQUEST_KEY}, set by {@code SelfRequestFilter} at request arrival, so it is
 * available here even though the egress lookup did not match.</p>
 */
@RequiredArgsConstructor
public class EgressGatewayFallbackHandler implements ChainedHandler {

  private final ChainedHandler gatewayHandler;

  @Override
  public Future<Boolean> handle(RoutingContext rc) {
    if (!isSelfRequest(rc)) {
      return succeededFuture(false);
    }
    return gatewayHandler.handle(rc);
  }
}
