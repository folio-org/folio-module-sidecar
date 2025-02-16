package org.folio.sidecar.service.routing;

import java.util.List;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;

public interface ModuleBootstrapListener {

  void onModuleBootstrap(ModuleBootstrapDiscovery moduleBootstrap);

  void onRequiredModulesBootstrap(List<ModuleBootstrapDiscovery> requiredModulesBootstrap);
}
