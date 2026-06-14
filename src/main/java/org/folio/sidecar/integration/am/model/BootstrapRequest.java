package org.folio.sidecar.integration.am.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@RegisterForReflection
public class BootstrapRequest {

  private String type;
  private Map<String, List<String>> tenants;

  public static BootstrapRequest ingress() {
    var request = new BootstrapRequest();
    request.type = "ingress";
    return request;
  }

  public static BootstrapRequest egress(Map<String, List<String>> tenants) {
    var request = new BootstrapRequest();
    request.type = "egress";
    request.tenants = tenants;
    return request;
  }
}
