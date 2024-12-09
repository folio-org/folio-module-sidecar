package org.folio.sidecar.configuration.properties;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "routing.handlers")
public interface RoutingHandlerProperties {

  Tracing tracing();

  interface Tracing {

    @WithDefault("false")
    boolean enabled();

    Optional<List<String>> paths();
  }
}
