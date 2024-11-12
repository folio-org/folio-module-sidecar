package org.folio.sidecar.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.SmallRyeConfig;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.folio.sidecar.configuration.properties.WebClientConfig;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WebClientConfigurationTest {

  @Inject Vertx vertx;
  @Inject WebClientConfig webClientConfig;
  @Inject WebClientConfiguration webClientConfiguration;

  @Test
  void webClient_positive() {
    var webClient = webClientConfiguration.webClient(vertx);
    assertThat(webClient).isNotNull();
  }

  /*@Test
  void webClientKeycloak_positive_withoutTruststore() {
    var properties = new KeycloakProperties();
    properties.setClientTlsEnabled(true);
    var webClientConfiguration = new WebClientConfiguration(
      null, properties, null, null);

    var webClient = webClientConfiguration.webClientKeycloak(vertx);
    assertThat(webClient).isNotNull();
  }*/

  @Test
  void webClientKeycloak_positive_withTruststore() {
    var webClient = webClientConfiguration.webClientKeycloak(vertx);
    assertThat(webClient).isNotNull();
  }

  /*@Test
  void webClientEgress_positive_withoutTruststore() {
    var properties = new SidecarProperties();
    properties.setClientTlsEnabled(true);
    var webClientConfiguration = new WebClientConfiguration(
      null, null, properties, null);

    var webClient = webClientConfiguration.webClientEgress(vertx);
    assertThat(webClient).isNotNull();
  }*/

  @Test
  void webClientEgress_positive_withTruststore() {
    var webClient = webClientConfiguration.webClientEgress(vertx);
    assertThat(webClient).isNotNull();
  }

  /*@Test
  void webClientGateway_positive_withoutTruststore() {
    var properties = new GatewayProperties();
    properties.setClientTlsEnabled(true);
    var webClientConfiguration = new WebClientConfiguration(
      null, null, null, properties);

    var webClient = webClientConfiguration.webClientGateway(vertx);
    assertThat(webClient).isNotNull();
  }*/

  @Test
  void webClientGateway_positive_withTruststore() {
    var webClient = webClientConfiguration.webClientGateway(vertx);
    assertThat(webClient).isNotNull();
  }

  public static final class WebClientConfigMockProducer {

    @Inject
    Config config;

    @Produces
    @ApplicationScoped
    @io.quarkus.test.Mock
    WebClientConfig webClientConfig() {
      var mapping = config.unwrap(SmallRyeConfig.class).getConfigMapping(WebClientConfig.class);

      var resultSpy = spy(mapping);

      var ingressSpy = spy(mapping.ingress());
      when(resultSpy.ingress()).thenReturn(ingressSpy);

      var egressSpy = spy(mapping.egress());
      when(resultSpy.egress()).thenReturn(egressSpy);

      var gatewaySpy = spy(mapping.gateway());
      when(resultSpy.gateway()).thenReturn(gatewaySpy);

      var keycloakSpy = spy(mapping.keycloak());
      when(resultSpy.keycloak()).thenReturn(keycloakSpy);

      return resultSpy;
    }
  }
}
