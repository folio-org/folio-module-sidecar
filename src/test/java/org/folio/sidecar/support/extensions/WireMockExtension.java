package org.folio.sidecar.support.extensions;

import static java.util.Map.entry;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class WireMockExtension implements QuarkusTestResourceConfigurableLifecycleManager<EnableWireMock> {

  private static final String WM_KEYSTORE_PATH = "certificates/test.keystore.jks";
  private static final String WM_KEYSTORE_TYPE = "JKS";
  private static final String WM_KEYSTORE_PASS = "SecretPassword";
  private static final String WM_KEYMANAGER_PASS = "SecretPassword";

  private WireMockServerManager serverManager;

  @Override
  public void init(EnableWireMock annotation) {
    serverManager = annotation.https()
      ? WireMockServerManager.withHttps(WM_KEYSTORE_PATH, WM_KEYSTORE_TYPE, WM_KEYSTORE_PASS, WM_KEYMANAGER_PASS,
          annotation.verbose())
      : WireMockServerManager.withHttp(annotation.verbose());
  }

  @Override
  public Map<String, String> start() {
    serverManager.start();

    var wiremockUrl = serverManager.getServerUrl();

    return Map.ofEntries(
      entry("MODULE_URL", wiremockUrl),
      entry("AM_CLIENT_URL", wiremockUrl),
      entry("TE_CLIENT_URL", wiremockUrl),
      entry("TM_CLIENT_URL", wiremockUrl),
      entry("KC_URL", wiremockUrl),
      entry("OKAPI_URL", wiremockUrl),
      entry("SIDECAR_FORWARD_UNKNOWN_REQUESTS_DESTINATION", wiremockUrl),
      entry("MOD_USERS_URL", wiremockUrl),
      entry("MOD_USERS_KEYCLOAK_URL", wiremockUrl)
    );
  }

  @Override
  public void stop() {
    serverManager.stop();
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(serverManager.getServer(),
      new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
  }

  private static final class WireMockServerManager {

    private final WireMockConfiguration configuration;
    private WireMockServer server;

    private WireMockServerManager(WireMockConfiguration configuration) {
      this.configuration = configuration;
    }

    static WireMockServerManager withHttp(boolean verbose) {
      var config = WireMockConfiguration.options()
        .dynamicPort()
        .globalTemplating(true)
        .notifier(new Slf4jNotifier(verbose));

      return new WireMockServerManager(config);
    }

    static WireMockServerManager withHttps(String keystorePath, String keystoreType, String keystorePassword,
        String keyManagerPassword, boolean verbose) {
      var config = WireMockConfiguration.options()
        .dynamicHttpsPort()
        .keystorePath(keystorePath)
        .keystoreType(keystoreType)
        .keystorePassword(keystorePassword)
        .keyManagerPassword(keyManagerPassword)
        .globalTemplating(true)
        .notifier(new Slf4jNotifier(verbose));

      return new WireMockServerManager(config);
    }

    WireMockServer getServer() {
      if (server == null) {
        throw new IllegalStateException("Wiremock server isn't initialized");
      }
      return server;
    }

    String getServerUrl() {
      return getServer().baseUrl();
    }

    void start() {
      if (server != null) {
        log.debug("Wiremock server already started at: {}", getServerUrl());
        return;
      }

      server = new WireMockServer(configuration);

      try {
        server.start();
        log.info("Wiremock server started at: {}", getServerUrl());
      } catch (Exception e) {
        server = null;
        throw new RuntimeException(e);
      }
    }

    void stop() {
      if (server != null) {
        try {
          var url = getServerUrl();
          server.stop();
          log.info("Wiremock server stopped at: {}", url);
        } finally {
          server = null;
        }
      }
    }
  }
}
