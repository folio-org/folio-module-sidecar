package org.folio.sidecar.utils;

import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Diagnostic timings captured per attempt, independent of asynchronous thread-local logging context. */
public final class OutboundRequestLog {

  private static final Logger LOG = LogManager.getLogger("org.folio.sidecar.outbound");
  private final boolean enabled;
  private final long start;
  private final String fields;
  private int responseStatus;

  private OutboundRequestLog(String operation, String method, String url, String tenant, String parentId) {
    enabled = LOG.isDebugEnabled();
    start = enabled ? System.nanoTime() : 0;
    fields = enabled ? "call_id=" + UUID.randomUUID() + " operation=" + clean(operation)
      + " method=" + clean(method) + " target=" + safeTarget(url)
      + " tenant=" + clean(tenant) + " parent_id=" + clean(parentId) : "";
    event("start", 0, null);
  }

  public static OutboundRequestLog start(String operation, String method, String url, String tenant, String parentId) {
    return new OutboundRequestLog(operation, method, url, tenant, parentId);
  }

  public static Future<HttpResponse<Buffer>> trace(String operation, String method, String url, String tenant,
    String parentId, Supplier<Future<HttpResponse<Buffer>>> action) {
    var trace = start(operation, method, url, tenant, parentId);
    if (!trace.enabled) {
      return action.get();
    }
    try {
      return action.get().onComplete(result -> trace.event("complete",
        result.succeeded() ? result.result().statusCode() : 0, result.cause()));
    } catch (RuntimeException error) {
      trace.event("complete", 0, error);
      throw error;
    }
  }

  public static String parentId(RoutingContext context) {
    var incoming = context.request().getHeader(REQUEST_ID);
    if (!LOG.isDebugEnabled() || incoming != null && !incoming.isBlank()) {
      return incoming;
    }
    String local = context.get("sidecar.outbound.parent");
    if (local == null) {
      local = "local-" + UUID.randomUUID();
      context.put("sidecar.outbound.parent", local);
    }
    return local;
  }

  public void forwarded(String requestId) {
    if (enabled) {
      LOG.debug("outbound {} phase=forwarded elapsed_ms={} outbound_id={}", fields,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), clean(requestId));
    }
  }

  public void event(String phase, int status, Throwable failure) {
    if (enabled) {
      if (status > 0) {
        responseStatus = status;
      }
      LOG.debug("outbound {} phase={} elapsed_ms={} status={} error_type={}", fields, clean(phase),
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), responseStatus,
        failure == null ? "-" : ExceptionUtils.getRootCause(failure).getClass().getSimpleName());
    }
  }

  static String safeTarget(String url) {
    try {
      var uri = URI.create(url);
      return clean(uri.getScheme() + "://" + uri.getHost()
        + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + uri.getRawPath());
    } catch (IllegalArgumentException error) {
      return "invalid-target";
    }
  }

  private static String clean(String value) {
    return value == null ? "-" : value.replaceAll("[\\s\\p{Cntrl}]", "_");
  }
}
