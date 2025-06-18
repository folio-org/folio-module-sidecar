package org.folio.sidecar.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@RegisterForReflection
public enum ModulePrefixStrategy {

  /**
   * Defines a proxy strategy for handing module name prefix in URL path.
   *
   * <pre>
   * → [gateway] /mod-foo/entities
   * → [sidecar] /mod-foo/entities (checks if the route can be found by /entities path part)
   * → [module container] handles /mod-foo/entities (proxy service inside removes /mod-foo prefix)
   * </pre>
   */
  PROXY("proxy"),

  /**
   * Defines a strip strategy for handing module name prefix in URL path.
   *
   * <pre>
   * → [gateway] /mod-foo/entities<br>
   * → [sidecar] /mod-foo/entities (checks if the route can be found by /entities path part)<br>
   * → [module container] handles /entities
   * </pre>
   */
  STRIP("strip"),

  /**
   * Defines a strategy that ignores module prefix in URL path.
   *
   * <pre>
   * → [gateway] /entities
   * → [sidecar] /entities (checks if the route can be found by /entities)
   * → [module container] handles /entities
   * </pre>
   */
  NONE("none");

  /**
   * A string value for enum value.
   */
  private final String value;
}
