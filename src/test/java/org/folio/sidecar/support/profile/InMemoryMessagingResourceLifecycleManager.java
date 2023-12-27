package org.folio.sidecar.support.profile;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import java.util.HashMap;
import java.util.Map;

public class InMemoryMessagingResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {

  private final Map<String, String> params = new HashMap<>();

  @Override
  public void init(Map<String, String> params) {
    this.params.putAll(params);
  }

  @Override
  public Map<String, String> start() {
    Map<String, String> env = new HashMap<>();

    for (var con : this.params.entrySet()) {
      switch (con.getValue()) {
        case "incoming" -> env.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(con.getKey()));
        case "outgoing" -> env.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(con.getKey()));
        default -> throw new IllegalArgumentException("Invalid connector type : " + con.getValue()
          + ". Expected: [incoming, outgoing]");
      }
    }

    return env;
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }
}
