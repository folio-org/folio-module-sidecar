package org.folio.sidecar.model;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class EntitlementsEvent {

  public static final String ENTITLEMENTS_EVENT = "entitlements";
  private Set<String> tenants;
}
