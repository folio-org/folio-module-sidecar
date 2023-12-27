package org.folio.support.profiles;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class ForwardUnknownTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
      "sidecar.forward-unknown-requests", "true"
    );
  }
}
