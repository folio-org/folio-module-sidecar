package org.folio.sidecar.integration.keycloak.filter;

import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.MODULE_URL;

import java.util.List;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.model.ScRoutingEntry;

public class AbstractFilterTest {

  protected ScRoutingEntry scRoutingEntry() {
    return scRoutingEntry(null, "foo.items.get");
  }

  protected ScRoutingEntry scRoutingEntry(String interfaceType, String... permissionRequired) {
    return scRoutingEntryWithId(interfaceType, "mod-foo-api-1.0", permissionRequired);
  }

  protected ScRoutingEntry scRoutingEntryWithId(String interfaceType, String interfaceId,
    String... permissionRequired) {
    var bootstrapEndpoint = moduleBootstrapEndpoint();
    bootstrapEndpoint.setPermissionsRequired(List.of(permissionRequired));
    return ScRoutingEntry.of(MODULE_ID, MODULE_URL, interfaceId, interfaceType, bootstrapEndpoint);
  }

  protected ModuleBootstrapEndpoint moduleBootstrapEndpoint() {
    return new ModuleBootstrapEndpoint("/foo/items", "GET");
  }
}
