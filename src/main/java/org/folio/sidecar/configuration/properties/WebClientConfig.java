package org.folio.sidecar.configuration.properties;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.util.Optional;

@ConfigMapping(prefix = "web-client")
public interface WebClientConfig {

  WebClientSettings ingress();

  WebClientSettings egress();

  WebClientSettings keycloak();

  WebClientSettings gateway();

  interface WebClientSettings {

    String name();

    @WithName("decompression-supported")
    @WithDefault("false")
    boolean decompression();

    TimeoutSettings timeout();

    PoolSettings pool();

    TlsSettings tls();
  }

  interface TimeoutSettings {

    @WithDefault("60") // in seconds
    int keepAlive();

    @WithDefault("0") // in seconds, 0 means don't timeout
    int idle();

    @WithDefault("0")
    int readIdle();

    @WithDefault("0") // in seconds, 0 means don't timeout
    int writeIdle();

    @WithDefault("60000") // in milliseconds
    int connect();
  }

  /*
   * Pool settings for WebClient
   *
   * The default values are taken from the Vert.x {@link io.vertx.core.http.PoolOptions} class
   */
  interface PoolSettings {
    
    @WithDefault("5")
    int maxSize();

    @WithDefault("1")
    int maxSizeHttp2();

    @WithDefault("1000") // in milliseconds
    int cleanerPeriod();

    @WithDefault("0")
    int eventLoopSize();

    @WithDefault("-1")
    int maxWaitQueueSize();
  }

  interface TlsSettings {

    @WithDefault("false")
    boolean enabled();

    Optional<String> trustStorePath();

    Optional<String> trustStorePassword();

    Optional<String> trustStoreFileType();

    Optional<String> trustStoreProvider();

    @WithDefault("false")
    boolean verifyHostname();
  }
}
