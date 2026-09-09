package org.folio.sidecar.configuration.properties;

import io.smallrye.config.ConfigMapping;

/**
 * Settings of the cache holding the module version a tenant is entitled to.
 */
@ConfigMapping(prefix = "module-binding")
public interface ModuleBindingProperties {

  CacheSettings cache();
}
