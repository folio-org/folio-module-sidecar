package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.folio.support.types.UnitTest;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class OutboundRequestLogTest {

  private final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("org.folio.sidecar.outbound");
  private final List<String> messages = new ArrayList<>();
  private Level previousLevel;
  private final Handler handler = new Handler() {
    @Override
    public void publish(LogRecord record) {
      messages.add(((ExtLogRecord) record).getFormattedMessage());
    }

    @Override
    public void flush() { }

    @Override
    public void close() { }
  };

  @BeforeEach
  void enableLogging() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.FINE);
    logger.addHandler(handler);
  }

  @AfterEach
  void restoreLogging() {
    logger.removeHandler(handler);
    logger.setLevel(previousLevel);
  }

  @Test
  void trace_positive_pendingThenCompleteAndSanitized() {
    var promise = Promise.<HttpResponse<Buffer>>promise();
    var result = OutboundRequestLog.trace("keycloak.authorization", "POST",
      "http://user:password@kc:8080/token?secret=value#fragment", "tenant", "parent\nforged", promise::future);
    assertThat(result.isComplete()).isFalse();
    assertThat(messages).hasSize(1);
    var response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(403);
    promise.complete(response);
    assertThat(result.result()).isSameAs(response);
    assertThat(messages).hasSize(2).allSatisfy(message -> assertThat(message)
      .contains("target=http://kc:8080/token", "parent_id=parent_forged", "call_id=")
      .doesNotContain("password", "secret", "fragment", "\n"));
    assertThat(messages.get(1)).contains("phase=complete", "status=403", "elapsed_ms=");
    assertThat(messages.get(0).split("call_id=")[1].split(" ")[0])
      .isEqualTo(messages.get(1).split("call_id=")[1].split(" ")[0]);
  }

  @Test
  void trace_negative_preservesFailureWithoutLoggingMessage() {
    var error = new IllegalStateException("token=secret");
    var result = OutboundRequestLog.trace("test", "GET", "http://module/path", null, null,
      () -> Future.failedFuture(error));
    assertThat(result.cause()).isSameAs(error);
    assertThat(messages.get(1)).contains("error_type=IllegalStateException").doesNotContain("secret");
  }

  @Test
  void trace_negative_synchronousFailureIsRethrown() {
    var error = new IllegalStateException("secret");
    assertThatThrownBy(() -> OutboundRequestLog.trace("test", "GET", "http://module/path", null, null,
      () -> {
        throw error;
      })).isSameAs(error);
    assertThat(messages.get(1)).contains("phase=complete").doesNotContain("secret");
  }

  @Test
  void event_positive_distinctAttemptsAndResponseStatus() {
    var first = OutboundRequestLog.start("forward", "GET", "http://module/path", "tenant", "parent");
    OutboundRequestLog.start("forward", "GET", "http://module/path", "tenant", "parent");
    assertThat(messages.get(0)).isNotEqualTo(messages.get(1));
    first.event("request-acquired", 0, null);
    first.event("response-headers", 200, null);
    first.event("complete", 0, null);
    first.forwarded("child;next\nforged");
    assertThat(messages.get(4)).contains("phase=complete", "status=200");
    assertThat(messages.get(5)).contains("phase=forwarded", "outbound_id=child;next_forged");
  }

  @Test
  void parentId_positive_stableLocalIdWithoutChangingHeaders() {
    var context = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    var values = new HashMap<String, Object>();
    when(context.request()).thenReturn(request);
    when(context.get("sidecar.outbound.parent")).thenAnswer(invocation -> values.get("parent"));
    org.mockito.Mockito.doAnswer(invocation -> {
      values.put("parent", invocation.getArgument(1));
      return context;
    }).when(context).put(org.mockito.ArgumentMatchers.eq("sidecar.outbound.parent"),
      org.mockito.ArgumentMatchers.anyString());
    var id = OutboundRequestLog.parentId(context);
    assertThat(id).startsWith("local-");
    assertThat(OutboundRequestLog.parentId(context)).isEqualTo(id);
    org.mockito.Mockito.verify(request, org.mockito.Mockito.times(2)).getHeader(REQUEST_ID);
    org.mockito.Mockito.verifyNoMoreInteractions(request);
  }

  @Test
  void trace_positive_disabledReturnsOriginalFuture() {
    logger.setLevel(Level.INFO);
    var future = Future.<HttpResponse<Buffer>>succeededFuture();
    assertThat(OutboundRequestLog.trace("test", "GET", "invalid URL secret", null, null, () -> future))
      .isSameAs(future);
    assertThat(messages).isEmpty();
  }
}
