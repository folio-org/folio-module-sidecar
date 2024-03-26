package org.folio.sidecar.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.folio.sidecar.configuration.properties.WebClientProperties;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@UnitTest
@ExtendWith(VertxExtension.class)
class WebClientConfigurationTest {

  private final WebClientConfiguration webClientConfiguration = new WebClientConfiguration(new WebClientProperties());

  @Test
  void webClient_positive(Vertx vertx) {
    var webClient = webClientConfiguration.webClient(vertx);
    assertThat(webClient).isNotNull();
  }
}
