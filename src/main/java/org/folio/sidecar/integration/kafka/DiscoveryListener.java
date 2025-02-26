package org.folio.sidecar.integration.kafka;

public interface DiscoveryListener {

  void onDiscovery(String moduleId);
}
