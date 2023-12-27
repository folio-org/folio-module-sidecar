package org.folio.sidecar.configuration;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.Produces;

@Dependent
public class WebClientConfiguration {

  /**
   * Creates {@link WebClient} component for outside http requests.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link WebClient} component.
   */
  @Produces
  @ApplicationScoped
  public WebClient webClient(Vertx vertx) {
    return WebClient.create(vertx);
  }
}
