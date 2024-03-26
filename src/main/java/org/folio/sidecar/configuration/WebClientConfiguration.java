package org.folio.sidecar.configuration;

import io.vertx.core.Vertx;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.folio.sidecar.configuration.properties.WebClientProperties;

@Dependent
@RequiredArgsConstructor
public class WebClientConfiguration {

  private final WebClientProperties webClientProperties;

  /**
   * Creates {@link WebClient} component for outside http requests.
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
   * Creates {@link WebClient} component for outside https requests.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link WebClient} component.
   */
  @Produces
  @Named("webClientTls")
  @ApplicationScoped
  public WebClient webClientTls(Vertx vertx) {
    return WebClient.create(vertx, createWebClientOptions());
  }

  private WebClientOptions createWebClientOptions() {
    return new WebClientOptions()
      .setVerifyHost(webClientProperties.isTlsHostnameVerified())
      .setTrustAll(false)
      .setTrustOptions(new KeyStoreOptions()
        .setPassword(webClientProperties.getTrustStorePassword())
        .setPath(webClientProperties.getTrustStoreFilePath())
        .setType(webClientProperties.getKeyStoreFileType())
        .setProvider(BouncyCastleFipsProvider.PROVIDER_NAME));
  }
}
