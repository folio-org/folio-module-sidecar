package org.folio.sidecar.integration.keycloak;

import static org.folio.sidecar.utils.RoutingUtils.getTenant;

import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.sidecar.utils.OutboundRequestLog;

/** Request metadata is captured before crossing the worker boundary; JWT contents are never logged. */
final class JwtParseLog {

  private static final Logger LOG = LogManager.getLogger("org.folio.sidecar.outbound");
  private final boolean enabled;
  private final String fields;
  private final long queuedAt;
  private volatile long startedAt;
  private volatile long finishedAt;

  JwtParseLog(RoutingContext context, String tokenType) {
    enabled = LOG.isDebugEnabled();
    fields = enabled ? "call_id=" + UUID.randomUUID() + " operation=jwt.parse token_type=" + clean(tokenType)
      + " tenant=" + clean(context == null ? null : getTenant(context))
      + " parent_id=" + clean(context == null ? null : OutboundRequestLog.parentId(context)) : "";
    if (enabled) {
      LOG.debug("outbound {} phase=queued elapsed_ms=0 queue_ms=-1 execution_ms=-1"
        + " delivery_ms=-1 error_type=-", fields);
    }
    queuedAt = enabled ? System.nanoTime() : 0;
  }

  static boolean isEnabled() {
    return LOG.isDebugEnabled();
  }

  void workerStarted() {
    if (enabled) {
      startedAt = System.nanoTime();
      event("worker-start", null);
    }
  }

  void workerCompleted(Throwable failure) {
    if (enabled) {
      finishedAt = System.nanoTime();
      event("worker-complete", failure);
    }
  }

  void complete(Throwable failure) {
    event("complete", failure);
  }

  private void event(String phase, Throwable failure) {
    if (enabled) {
      var now = System.nanoTime();
      LOG.debug("outbound {} phase={} elapsed_ms={} queue_ms={} execution_ms={} delivery_ms={} error_type={}",
        fields, phase, millis(now - queuedAt), startedAt == 0 ? -1 : millis(startedAt - queuedAt),
        finishedAt == 0 ? -1 : millis(finishedAt - startedAt),
        finishedAt == 0 || !"complete".equals(phase) ? -1 : millis(now - finishedAt),
        failure == null ? "-" : ExceptionUtils.getRootCause(failure).getClass().getSimpleName());
    }
  }

  private static long millis(long nanos) {
    return TimeUnit.NANOSECONDS.toMillis(nanos);
  }

  private static String clean(String value) {
    return value == null ? "-" : value.replaceAll("[\\s\\p{Cntrl}]", "_");
  }
}
