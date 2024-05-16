package org.folio.sidecar.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.folio.sidecar.configuration.properties.GatewayProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WebClientConfigurationTest {

  @Inject Vertx vertx;
  @Inject WebClientConfiguration webClientConfiguration;

  @Test
  void webClient_positive() {
    var webClient = webClientConfiguration.webClient(vertx);
    assertThat(webClient).isNotNull();
  }

  @Test
  void webClientKeycloak_positive_withoutTruststore() {
    var properties = new KeycloakProperties();
    properties.setClientTlsEnabled(true);
    var webClientConfiguration = new WebClientConfiguration(
      null, properties, null, null);

    var webClient = webClientConfiguration.webClientKeycloak(vertx);
    assertThat(webClient).isNotNull();
  }

  @Test
  void webClientKeycloak_positive_withTruststore() {
    var webClient = webClientConfiguration.webClientKeycloak(vertx);
    assertThat(webClient).isNotNull();
  }

  @Test
  void webClientEgress_positive_withoutTruststore() {
    var properties = new SidecarProperties();
    properties.setClientTlsEnabled(true);
    var webClientConfiguration = new WebClientConfiguration(
      null, null, properties, null);

    var webClient = webClientConfiguration.webClientEgress(vertx);
    assertThat(webClient).isNotNull();
  }

  @Test
  void webClientEgress_positive_withTruststore() {
    var webClient = webClientConfiguration.webClientEgress(vertx);
    assertThat(webClient).isNotNull();
  }

  @Test
  void webClientGateway_positive_withoutTruststore() {
    var properties = new GatewayProperties();
    properties.setClientTlsEnabled(true);
    var webClientConfiguration = new WebClientConfiguration(
      null, null, null, properties);

    var webClient = webClientConfiguration.webClientGateway(vertx);
    assertThat(webClient).isNotNull();
  }

  @Test
  void webClientGateway_positive_withTruststore() {
    var webClient = webClientConfiguration.webClientGateway(vertx);
    assertThat(webClient).isNotNull();
  }
}
