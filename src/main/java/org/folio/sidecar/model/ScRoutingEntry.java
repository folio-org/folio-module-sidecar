package org.folio.sidecar.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;

@Data
@RequiredArgsConstructor(staticName = "of")
public class ScRoutingEntry {

  public static final String GATEWAY_INTERFACE_ID = "GATEWAY";
  /**
   * Module id.
   */
  private final String moduleId;

  /**
   * Sidecar URL.
   */
  private final String location;

  /**
   * Interface identifier.
   */
  private final String interfaceId;

  /**
   * Interface type.
   */
  private final String interfaceType;

  /**
   * Corresponding routing entry.
   */
  private final ModuleBootstrapEndpoint routingEntry;

  /**
   * Creates sidecar routing entry without interface type.
   *
   * @param moduleId module id
   * @param location discovery location
   * @param interfaceId interface id
   * @param routingEntry routing entry
   * @return sidecar routing entry.
   */
  public static ScRoutingEntry of(String moduleId, String location, String interfaceId,
    ModuleBootstrapEndpoint routingEntry) {
    return new ScRoutingEntry(moduleId, location, interfaceId, null, routingEntry);
  }

  public static ScRoutingEntry gatewayRoutingEntry(String location) {
    return ScRoutingEntry.of("NONE", location, GATEWAY_INTERFACE_ID, null);
  }

  public static ScRoutingEntry gatewayRoutingEntry(String location, String moduleId) {
    return ScRoutingEntry.of(moduleId, location, GATEWAY_INTERFACE_ID, null);
  }
}
