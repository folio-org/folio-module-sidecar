package org.folio.sidecar.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@RegisterForReflection
public class EntitlementsEvent {

  public static final String ENTITLEMENTS_EVENT = "entitlements";
  private Set<String> tenants;
}
