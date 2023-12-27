package org.folio.sidecar.support.profile;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

public class InMemoryMessagingTestProfile implements QuarkusTestProfile {

  @Override
  public String getConfigProfile() {
    return "im-msg-test";
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    Map<String, String> env = new HashMap<>();

    env.put("quarkus.kafka.devservices.enabled", "false");

    return env;
  }
}
