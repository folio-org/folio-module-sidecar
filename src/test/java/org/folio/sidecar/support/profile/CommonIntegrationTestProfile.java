package org.folio.sidecar.support.profile;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class CommonIntegrationTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
      "module.name", "mod-foo",
      "module.version", "0.2.1",
      "sidecar.url", "http://test-sidecar:8081"
    );
  }
}
