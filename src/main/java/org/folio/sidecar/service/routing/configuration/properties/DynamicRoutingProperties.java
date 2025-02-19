package org.folio.sidecar.service.routing.configuration.properties;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.folio.sidecar.configuration.properties.CacheSettings;

@ConfigMapping(prefix = "routing.dynamic")
public interface DynamicRoutingProperties {

  @WithDefault("false")
  boolean enabled();

  @WithName("discovery.cache")
  CacheSettings discoveryCache();
}
