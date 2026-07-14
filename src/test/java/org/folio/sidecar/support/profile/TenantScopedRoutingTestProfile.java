package org.folio.sidecar.support.profile;

import java.util.HashMap;
import java.util.Map;

public class TenantScopedRoutingTestProfile extends InMemoryMessagingTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    var overrides = new HashMap<>(super.getConfigOverrides());
    overrides.put("routing.tenant-scoped.enabled", "true");
    return overrides;
  }
}
