package org.folio.sidecar.service.filter;

import static org.folio.sidecar.utils.RoutingUtils.ADD_MODULE_NAME_TO_PATH_KEY;
import static org.folio.sidecar.utils.RoutingUtils.SELF_REQUEST_KEY;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.service.SidecarSignatureService;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class SelfRequestFilter implements IngressRequestFilter {

  private final SidecarProperties sidecarProperties;
  private final SidecarSignatureService sidecarSignatureService;

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    var selfRequest = sidecarSignatureService.isSelfRequest(routingContext);
    routingContext.put(SELF_REQUEST_KEY, selfRequest);

    if (selfRequest) {
      var rq = routingContext.request();
      log.info("Request is self request, skipping authorization: method = {}, path = {}", rq.method(), rq.path());
      routingContext.put(ADD_MODULE_NAME_TO_PATH_KEY, sidecarProperties.isModulePrefixEnabled());
    }

    return Future.succeededFuture(routingContext);
  }

  @Override
  public int getOrder() {
    return 100;
  }
}
