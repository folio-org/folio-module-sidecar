package org.folio.sidecar.integration.kafka;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@RegisterForReflection
public class DiscoveryEvent {

  private String moduleId;
}
