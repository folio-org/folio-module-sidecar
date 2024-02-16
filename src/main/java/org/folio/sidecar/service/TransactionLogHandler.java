package org.folio.sidecar.service;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.okapi.OkapiHeaders;

@Log4j2(topic = "transaction")
@ApplicationScoped
public class TransactionLogHandler {

  public void handle(RoutingContext rc, HttpResponse<Buffer> resp, HttpRequest<Buffer> req) {
    var request = rc.request();

    var date = new Date();
    var formatter = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss z");
    String strDate= formatter.format(date);

    //todo redo with quarcus log pattern
    var end = System.currentTimeMillis();
    log.info(" {} - {} - {} [{}] {} {} {} {} {} rt={} uct={} uht={} urt={} {} {} {} {}",
      request.remoteAddress(), request.authority(), request.getHeader("X-Remote-User"),
      strDate, request.method(), request.path(), request.version(),
      resp.statusCode(), resp.body().getBytes().length,
      (end - (long) rc.get("rt"))/1000f, (end - (long) rc.get("uct"))/1000f,
      (end - (long) rc.get("uht"))/1000f, (end - (long) rc.get("urt"))/1000f,
      request.getHeader(HttpHeaders.USER_AGENT), request.getHeader(OkapiHeaders.TENANT),
      request.getHeader(OkapiHeaders.USER_ID),
      req.headers().get(OkapiHeaders.REQUEST_ID));
  }
}
