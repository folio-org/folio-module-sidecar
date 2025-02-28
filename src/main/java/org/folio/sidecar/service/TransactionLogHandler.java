package org.folio.sidecar.service;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.ThreadContext;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.utils.StringUtils;

@Log4j2(topic = "transaction")
@ApplicationScoped
public class TransactionLogHandler {

  public void log(RoutingContext rc, HttpClientResponse resp, HttpClientRequest req) {
    var request = rc.request();
    var end = System.currentTimeMillis();
    String forwardedFor = request.getHeader("X-Forwarded-For");
    String remoteIp = StringUtils.isEmpty(forwardedFor) ? String.valueOf(request.remoteAddress()) : forwardedFor;
    ThreadContext.put("remote-ip", remoteIp);
    ThreadContext.put("remote-host", String.valueOf(request.authority()));
    ThreadContext.put("remote-user", request.getHeader("X-Remote-User"));
    ThreadContext.put("method", String.valueOf(request.method()));
    ThreadContext.put("path", request.path());
    ThreadContext.put("uri", request.uri());
    ThreadContext.put("protocol", String.valueOf(request.version()));
    ThreadContext.put("status", String.valueOf(resp.statusCode()));
    ThreadContext.put("bytes", "0");
    ThreadContext.put("user-agent", request.getHeader(HttpHeaders.USER_AGENT));
    ThreadContext.put("x-okapi-tenant", request.getHeader(OkapiHeaders.TENANT));
    ThreadContext.put("x-okapi-user-id", request.getHeader(OkapiHeaders.USER_ID));
    ThreadContext.put("x-okapi-request-id", req.headers().get(OkapiHeaders.REQUEST_ID));
    ThreadContext.put("rt", calculateValue(rc, end, "rt"));
    ThreadContext.put("uct", calculateValue(rc, end, "uct"));
    ThreadContext.put("uht", calculateValue(rc, end, "uht"));
    ThreadContext.put("urt", calculateValue(rc, end, "urt"));

    try {
      log.info("");
    } finally {
      ThreadContext.clearMap();
    }
  }

  private String calculateValue(RoutingContext rc, long end, String name) {
    return rc.get(name) != null ? String.valueOf((end - (long) rc.get(name)) / 1000d) : "";
  }
}
