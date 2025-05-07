package org.folio.sidecar.service.routing.handler;

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.RoutingUtils.dumpHeaders;
import static org.folio.sidecar.utils.RoutingUtils.dumpUri;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class TraceHeadersHandler implements Handler<RoutingContext> {

  private final Handler<RoutingContext> decorated;
  private final Collection<String> paths;

  @Override
  public void handle(RoutingContext rc) {
    var req = rc.request();
    if (pathMatched(req.path())) {
      log.info("""
        \n======================================
        Request: method = {}, uri = {}
        Current state of request context:
        ********** Headers *******************
        {}""", req::method, dumpUri(rc), dumpHeaders(rc));
    }
    decorated.handle(rc);
  }

  private boolean pathMatched(@Nullable String path) {
    return isEmpty(paths) || paths.stream().anyMatch(s -> containsIgnoreCase(path, s));
  }
}
