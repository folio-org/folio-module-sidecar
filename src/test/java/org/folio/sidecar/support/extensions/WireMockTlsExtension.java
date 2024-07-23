package org.folio.sidecar.support.extensions;

import static java.util.Map.entry;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class WireMockTlsExtension implements QuarkusTestResourceLifecycleManager {

  public static WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    var config = WireMockConfiguration.options().dynamicHttpsPort()
      .keystorePath("certificates/bcfks.keystore.jks").keystorePassword("secretpassword")
      .keyManagerPassword("secretpassword").globalTemplating(true);

    wireMockServer = new WireMockServer(config);
    wireMockServer.start();
    var wiremockUrl = String.format("https://localhost:%s", wireMockServer.httpsPort());
    log.info("Wiremock server started at: {}", wiremockUrl);

    return Map.ofEntries(
      entry("MODULE_NAME", "mod-foo"),
      entry("MODULE_VERSION", "0.2.1"),
      entry("MODULE_URL", wiremockUrl),
      entry("AM_CLIENT_URL", wiremockUrl),
      entry("TE_CLIENT_URL", wiremockUrl),
      entry("TM_CLIENT_URL", wiremockUrl),
      entry("KC_URL", wiremockUrl),
      entry("OKAPI_URL", wiremockUrl),
      entry("OKAPI_TOKEN", "T2thcGkgdGVzdCBhdXRoIHRva2Vu"),
      entry("SIDECAR_URL", "http://test-sidecar:8081"),
      entry("SIDECAR_FORWARD_UNKNOWN_REQUESTS_DESTINATION", wiremockUrl),
      entry("MOD_USERS_KEYCLOAK_URL", wiremockUrl)
    );
  }

  @Override
  public void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
      log.info("Wiremock server stopped");
      wireMockServer = null;
    }
  }
}
