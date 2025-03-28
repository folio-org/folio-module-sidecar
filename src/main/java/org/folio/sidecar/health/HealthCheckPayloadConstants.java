package org.folio.sidecar.health;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HealthCheckPayloadConstants {

  public static final String CHECKS_KEY = "checks";
  public static final String STATUS_KEY = "status";
  public static final String NAME_KEY = "name";
}
