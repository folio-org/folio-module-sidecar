package org.folio.sidecar.support.extensions;

import static java.util.Map.entry;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@RequiredArgsConstructor
public class WireMockExtension implements QuarkusTestResourceLifecycleManager {

  public static WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    var wireMockConfiguration = WireMockConfiguration.options().dynamicPort().globalTemplating(true);
    wireMockServer = new WireMockServer(wireMockConfiguration);
    wireMockServer.start();
    var wiremockUrl = String.format("http://localhost:%s", wireMockServer.port());
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
