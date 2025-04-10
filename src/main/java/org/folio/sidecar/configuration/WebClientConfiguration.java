package org.folio.sidecar.configuration;

import static java.lang.String.format;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.sidecar.configuration.properties.WebClientConfig;
import org.folio.sidecar.configuration.properties.WebClientConfig.WebClientSettings;

@Log4j2
@Dependent
@RequiredArgsConstructor
public class WebClientConfiguration {

  private final WebClientConfig webClientConfig;

  /**
   * Creates {@link HttpClient} component for outside HTTP requests.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link HttpClient} component.
   */
  @Produces
  @Named("httpClient")
  @ApplicationScoped
  public HttpClient httpClient(Vertx vertx) {
    return createHttpClient(webClientConfig.ingress(), vertx);
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
    return createWebClient(webClientConfig.keycloak(), vertx);
  }

  /**
   * Creates {@link HttpClient} component for egress HTTPS requests otherwise HTTP {@link HttpClient} is returned.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link HttpClient} component.
   */
  @Produces
  @ApplicationScoped
  @Named("httpClientEgress")
  public HttpClient httpClientEgress(Vertx vertx) {
    return createHttpClient(webClientConfig.egress(), vertx);
  }

  @Produces
  @ApplicationScoped
  @Named("webClientEgress")
  public WebClient webClientEgress(Vertx vertx) {
    return createWebClient(webClientConfig.egress(), vertx);
  }

  /**
   * Creates {@link HttpClient} component for outside HTTPS requests to Gateway otherwise HTTP {@link HttpClient} is
   * returned.
   *
   * @param vertx - {@link Vertx} context from quarkus.
   * @return created {@link HttpClient} component.
   */
  @Produces
  @ApplicationScoped
  @Named("httpClientGateway")
  public HttpClient httpClientGateway(Vertx vertx) {
    return createHttpClient(webClientConfig.gateway(), vertx);
  }

  private WebClient createWebClient(WebClientSettings settings, Vertx vertx) {
    var options = populateOptionsFrom(settings);
    return WebClient.create(vertx, options);
  }

  private HttpClient createHttpClient(WebClientSettings settings, Vertx vertx) {
    var options = populateOptionsFrom(settings);
    return vertx.createHttpClient(options);
  }

  private WebClientOptions populateOptionsFrom(WebClientSettings settings) {
    var result = new WebClientOptions()
      .setName(settings.name())
      .setDecompressionSupported(settings.decompression())
      .setKeepAlive(true)
      // timeouts
      .setConnectTimeout(settings.timeout().connect())
      .setKeepAliveTimeout(settings.timeout().keepAlive())
      .setIdleTimeout(settings.timeout().idle())
      .setReadIdleTimeout(settings.timeout().readIdle())
      .setWriteIdleTimeout(settings.timeout().writeIdle())
      // pool settings
      .setMaxPoolSize(settings.pool().maxSize())
      .setHttp2MaxPoolSize(settings.pool().maxSizeHttp2())
      .setPoolCleanerPeriod(settings.pool().cleanerPeriod())
      .setPoolEventLoopSize(settings.pool().eventLoopSize())
      .setMaxWaitQueueSize(settings.pool().maxWaitQueueSize());
    log.info("Creating web client with options: clientName = {}, options = {}", settings::name,
      () -> optionsToString(result));

    var tls = settings.tls();
    if (tls.enabled()) {
      if (tls.trustStorePath().isEmpty()) {
        log.debug("Creating web client for Public Trusted Certificates: clientName = {}", settings.name());
        result.setSsl(true)
          .setReuseAddress(true)
          .setReusePort(true)
          .setTcpKeepAlive(true)
          .setTrustAll(false);
      } else {
        result.setVerifyHost(tls.verifyHostname())
          .setSsl(true)
          .setReuseAddress(true)
          .removeEnabledSecureTransportProtocol("TLSv1.3")
          .addEnabledSecureTransportProtocol("TLSv1.2")
          .setReusePort(true)
          .setTcpKeepAlive(true)
          .setTrustAll(false)
          .setTrustOptions(new KeyStoreOptions()
            .setPassword(getRequired(tls.trustStorePassword(), "trust-store-password", settings.name()))
            .setPath(getRequired(tls.trustStorePath(), "trust-store-path", settings.name()))
            .setType(getRequired(tls.trustStoreFileType(), "trust-store-file-type", settings.name()))
            .setProvider(getRequired(tls.trustStoreProvider(), "trust-store-provider", settings.name())));
      }
    }

    return result;
  }

  private static String optionsToString(WebClientOptions result) {
    return new ToStringBuilder(result)
      .append("isDecompressionSupported", result.isDecompressionSupported())
      .append("connectTimeout", result.getConnectTimeout())
      .append("keepAliveTimeout", result.getKeepAliveTimeout())
      .append("idleTimeout", result.getIdleTimeout())
      .append("readIdleTimeout", result.getReadIdleTimeout())
      .append("writeIdleTimeout", result.getWriteIdleTimeout())
      .append("maxPoolSize", result.getMaxPoolSize())
      .append("http2MaxPoolSize", result.getHttp2MaxPoolSize())
      .append("poolCleanerPeriod", result.getPoolCleanerPeriod())
      .append("poolEventLoopSize", result.getPoolEventLoopSize())
      .append("maxWaitQueueSize", result.getMaxWaitQueueSize())
      .toString();
  }

  private static String getRequired(Optional<String> optional, String property, String client) {
    return optional.orElseThrow(() -> new IllegalArgumentException(
      format("Client configuration missing required property: client = %s, property = %s", client, property)));
  }
}
