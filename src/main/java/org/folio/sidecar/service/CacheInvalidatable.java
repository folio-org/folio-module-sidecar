package org.folio.sidecar.service;

import org.folio.sidecar.integration.kafka.LogoutEvent;

public interface CacheInvalidatable {

  void invalidate(LogoutEvent event);
}
