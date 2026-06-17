package org.folio.sidecar.service.routing;

import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;

public interface ModuleBootstrapListener {

  default void onModuleBootstrap(ModuleBootstrapDiscovery moduleBootstrap, ChangeType changeType) {}

  enum ChangeType {
    INIT,
    UPDATE
  }
}
