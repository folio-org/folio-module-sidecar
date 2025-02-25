package org.folio.sidecar.integration.am.model;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.Data;

@Data
@RegisterForReflection
public class ModuleBootstrapEndpoint {

  private String[] methods;
  private String pathPattern;
  private String path;
  private List<String> permissionsRequired;
  private List<String> permissionsDesired;

  /**
   * Constructor.
   */
  public ModuleBootstrapEndpoint() {}

  /**
   * Constructor (utility).
   *
   * @param pathPattern pattern
   * @param methods HTTP method
   */
  public ModuleBootstrapEndpoint(String pathPattern, String... methods) {
    this.pathPattern = pathPattern;
    this.methods = methods;
  }

  /**
   * Get path pattern/path - whichever exist.
   */
  @JsonIgnore
  public String getStaticPath() {
    return isEmpty(path) ? pathPattern : path;
  }
}
