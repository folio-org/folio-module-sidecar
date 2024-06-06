package org.folio.sidecar.support.profile;

import java.util.HashMap;
import java.util.Map;

public class InMemoryMessagingTestProfile extends CommonIntegrationTestProfile {

  @Override
  public String getConfigProfile() {
    return "im-msg-test";
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    var result = new HashMap<>(super.getConfigOverrides());

    result.put("quarkus.kafka.devservices.enabled", "false");

    return result;
  }
}
