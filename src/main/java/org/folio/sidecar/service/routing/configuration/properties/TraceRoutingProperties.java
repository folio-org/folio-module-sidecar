package org.folio.sidecar.service.routing.configuration.properties;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "routing.tracing")
public interface TraceRoutingProperties {

  @WithDefault("false")
  boolean enabled();

  Optional<List<String>> paths();
}
