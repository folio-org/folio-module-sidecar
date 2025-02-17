package org.folio.sidecar.service.routing;

import java.util.List;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;

public interface ModuleBootstrapListener {

  default void onModuleBootstrap(ModuleBootstrapDiscovery moduleBootstrap, ChangeType changeType) {}

  default void onRequiredModulesBootstrap(List<ModuleBootstrapDiscovery> requiredModulesBootstrap,
    ChangeType changeType) {}

  enum ChangeType {
    INIT,
    UPDATE
  }
}
