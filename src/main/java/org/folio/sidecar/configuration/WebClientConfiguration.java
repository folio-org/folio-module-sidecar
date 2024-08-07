package org.folio.sidecar.configuration;

import static org.folio.sidecar.utils.StringUtils.isBlank;

import io.vertx.core.Vertx;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.configuration.properties.GatewayProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.configuration.properties.WebClientProperties;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;

@Log4j2
@Dependent
@RequiredArgsConstructor
public class WebClientConfiguration {

  private final WebClientProperties webClientProperties;
  private final KeycloakProperties keycloakProperties;
  private final SidecarProperties sidecarProperties;
  private final GatewayProperties gatewayProperties;

  /**
   * Creates {@link WebClient} component for outside HTTP requests.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link WebClient} component.
   */
  @Produces
  @Named("webClient")
  @ApplicationScoped
  public WebClient webClient(Vertx vertx) {
    return WebClient.create(vertx);
  }

  /**
   * Creates {@link WebClient} component for Keycloak HTTPS requests if TLS is enabled otherwise HTTP {@link WebClient}
   * is returned.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link WebClient} component.
   */
  @Produces
  @ApplicationScoped
  @Named("webClientKeycloak")
  public WebClient webClientKeycloak(Vertx vertx) {
    if (keycloakProperties.isClientTlsEnabled()) {
      if (isBlank(keycloakProperties.getTrustStorePath())) {
        return getPublicTrustedCertWebClient(vertx);
      }
      return WebClient.create(vertx, webClientOptions(new KeyStoreOptions()
        .setPassword(keycloakProperties.getTrustStorePassword())
        .setPath(keycloakProperties.getTrustStorePath())
        .setType(keycloakProperties.getTrustStoreFileType())
        .setProvider(keycloakProperties.getTrustStoreProvider())));
    }
    return webClient(vertx);
  }

  /**
   * Creates {@link WebClient} component for egress HTTPS requests otherwise HTTP {@link WebClient} is returned.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link WebClient} component.
   */
  @Produces
  @ApplicationScoped
  @Named("webClientEgress")
  public WebClient webClientEgress(Vertx vertx) {
    if (sidecarProperties.isClientTlsEnabled()) {
      if (isBlank(sidecarProperties.getTrustStorePath())) {
        return getPublicTrustedCertWebClient(vertx);
      }
      return WebClient.create(vertx, webClientOptions(new KeyStoreOptions()
        .setPassword(sidecarProperties.getTrustStorePassword())
        .setPath(sidecarProperties.getTrustStorePath())
        .setType(sidecarProperties.getTrustStoreFileType())
        .setProvider(sidecarProperties.getTrustStoreProvider())));
    }
    return webClient(vertx);
  }

  /**
   * Creates {@link WebClient} component for outside HTTPS requests to Gateway otherwise HTTP {@link WebClient} is
   * returned.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link WebClient} component.
   */
  @Produces
  @ApplicationScoped
  @Named("webClientGateway")
  public WebClient webClientGateway(Vertx vertx) {
    if (gatewayProperties.isClientTlsEnabled()) {
      if (isBlank(gatewayProperties.getTrustStorePath())) {
        return getPublicTrustedCertWebClient(vertx);
      }
      return WebClient.create(vertx, webClientOptions(new KeyStoreOptions()
        .setPassword(gatewayProperties.getTrustStorePassword())
        .setPath(gatewayProperties.getTrustStorePath())
        .setType(gatewayProperties.getTrustStoreFileType())
        .setProvider(gatewayProperties.getTrustStoreProvider())));
    }
    return webClient(vertx);
  }

  private WebClient getPublicTrustedCertWebClient(Vertx vertx) {
    log.debug("Creating WebClient for Public Trusted Certificates");
    return WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustAll(false));
  }

  private WebClientOptions webClientOptions(KeyStoreOptions options) {
    return new WebClientOptions()
      .setVerifyHost(webClientProperties.isTlsHostnameVerified())
      .setTrustAll(false)
      .setTrustOptions(options);
  }
}
