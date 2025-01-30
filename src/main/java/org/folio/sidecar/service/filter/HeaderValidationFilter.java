package org.folio.sidecar.service.filter;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.model.error.Error;
import org.folio.sidecar.model.error.ErrorCode;
import org.folio.sidecar.model.error.ErrorResponse;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@ApplicationScoped
public class HeaderValidationFilter implements RequestFilter {

  private static final String OKAPI_HEADER_PREFIX = "x-okapi-";

  @Override
  public Future<RoutingContext> filter(RoutingContext routingContext) {
    var headers = routingContext.request().headers();
    var duplicateOkapiHeaders = findDuplicateOkapiHeaders(headers);

    if (!duplicateOkapiHeaders.isEmpty()) {
      var errorMessage = String.format("Request contains duplicate X-Okapi headers: %s",
        String.join(", ", duplicateOkapiHeaders));
      
      var error = new Error()
        .type("ValidationError")
        .code(ErrorCode.VALIDATION_ERROR)
        .message(errorMessage);

      var errorResponse = new ErrorResponse()
        .errors(List.of(error))
        .totalRecords(1);

      routingContext.response()
        .setStatusCode(Response.Status.BAD_REQUEST.getStatusCode())
        .putHeader("Content-Type", "application/json")
        .end(JsonObject.mapFrom(errorResponse).encode());

      return Future.failedFuture(errorMessage);
    }

    return Future.succeededFuture(routingContext);
  }

  private List<String> findDuplicateOkapiHeaders(io.vertx.core.MultiMap headers) {
    return headers.names().stream()
      .filter(name -> name.toLowerCase().startsWith(OKAPI_HEADER_PREFIX))
      .filter(name -> headers.getAll(name).size() > 1)
      .collect(Collectors.toList());
  }
}