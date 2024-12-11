package org.folio.sidecar.service.filter;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseStackTrace;
import static org.folio.sidecar.utils.CollectionUtils.sortByOrder;
import static org.folio.sidecar.utils.RoutingUtils.dumpContextData;
import static org.folio.sidecar.utils.RoutingUtils.dumpHeaders;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Log4j2
@ApplicationScoped
public class RequestFilterService {

  private final List<IngressRequestFilter> ingressRequestFilters;
  private final List<EgressRequestFilter> egressRequestFilters;
  private final boolean tracingOnError;

  @Inject
  public RequestFilterService(Instance<IngressRequestFilter> ingressRequestFilters,
    Instance<EgressRequestFilter> egressRequestFilters,
    @ConfigProperty(name = "filters.tracing.on-error", defaultValue = "false") boolean tracingOnError) {
    this.ingressRequestFilters = sortByOrder(ingressRequestFilters);
    this.egressRequestFilters = sortByOrder(egressRequestFilters);
    this.tracingOnError = tracingOnError;
  }

  public Future<RoutingContext> filterIngressRequest(RoutingContext routingContext) {
    return applyFilterChain(routingContext, ingressRequestFilters);
  }

  public Future<RoutingContext> filterEgressRequest(RoutingContext routingContext) {
    return applyFilterChain(routingContext, egressRequestFilters);
  }

  private Future<RoutingContext> applyFilterChain(RoutingContext rc, List<? extends RequestFilter> filters) {
    if (filters.isEmpty()) {
      return Future.succeededFuture(rc);
    }

    var filter = filters.get(0);
    var filterFuture = filter.applyFilter(rc);
    for (int i = 1; i < filters.size(); i++) {
      var currentFilter = filters.get(i);
      filterFuture = filterFuture.compose(currentFilter::applyFilter);
    }

    return !tracingOnError ? filterFuture : filterFuture.onFailure(traceContext(rc));
  }

  private static Handler<Throwable> traceContext(RoutingContext rc) {
    return throwable -> {
      log.debug("Exception happened while applying filters: error = {}, {}",
        throwable::getMessage, () -> getCausePlace(throwable));

      log.debug("""
        Current state of request context:
        ********** Headers *******************
        {}
        ********** Context Data **************
        {}
        """, () -> dumpHeaders(rc), () -> dumpContextData(rc));
    };
  }

  private static String getCausePlace(Throwable throwable) {
    String[] st = getRootCauseStackTrace(throwable);
    return Arrays.stream(st).skip(1).findFirst().orElse("Unknown");
  }
}
