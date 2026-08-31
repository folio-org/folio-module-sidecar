package org.folio.sidecar.service.filter;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.utils.RoutingUtils.REQUEST_STAGE_KEY;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.stream.Stream;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RequestFilterServiceTest {

  @Mock private RoutingContext routingContext;

  @Test
  void filterIngressRequest_positive_recordsStagePerFilter() {
    var service = ingressFilterService(new FirstTestFilter(), new SecondTestFilter());

    var result = service.filterIngressRequest(routingContext);

    assertThat(result.succeeded()).isTrue();
    var inOrder = inOrder(routingContext);
    inOrder.verify(routingContext).put(REQUEST_STAGE_KEY, "FirstTestFilter");
    inOrder.verify(routingContext).put(REQUEST_STAGE_KEY, "SecondTestFilter");
  }

  @Test
  void filterIngressRequest_positive_recordsStageForSkippedFilter() {
    var service = ingressFilterService(new SkippedTestFilter());

    var result = service.filterIngressRequest(routingContext);

    assertThat(result.succeeded()).isTrue();
    verify(routingContext).put(REQUEST_STAGE_KEY, "SkippedTestFilter");
  }

  /*
   * The filters must be hand-written classes: a Mockito mock is named IngressRequestFilter$MockitoMock$123, which is
   * not the stage name the service reports.
   */
  private static RequestFilterService ingressFilterService(IngressRequestFilter... filters) {
    return new RequestFilterService(createInstance(List.of(filters)), createInstance(List.of()), false);
  }

  @SuppressWarnings("unchecked")
  private static <T> Instance<T> createInstance(List<T> items) {
    Instance<T> instance = mock(Instance.class);
    when(instance.stream()).thenReturn((Stream<T>) items.stream());
    return instance;
  }

  private static final class FirstTestFilter implements IngressRequestFilter {

    @Override
    public Future<RoutingContext> filter(RoutingContext routingContext) {
      return succeededFuture(routingContext);
    }

    @Override
    public int getOrder() {
      return 1;
    }
  }

  private static final class SecondTestFilter implements IngressRequestFilter {

    @Override
    public Future<RoutingContext> filter(RoutingContext routingContext) {
      return succeededFuture(routingContext);
    }

    @Override
    public int getOrder() {
      return 2;
    }
  }

  private static final class SkippedTestFilter implements IngressRequestFilter {

    @Override
    public Future<RoutingContext> filter(RoutingContext routingContext) {
      throw new IllegalStateException("Skipped filter must not be applied");
    }

    @Override
    public boolean shouldSkip(RoutingContext routingContext) {
      return true;
    }

    @Override
    public int getOrder() {
      return 1;
    }
  }
}
