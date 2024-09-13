package org.folio.sidecar.integration.keycloak;

import static org.folio.sidecar.model.EntitlementsEvent.ENTITLEMENTS_EVENT;

import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.jwt.openid.OpenidJwtParserProvider;
import org.folio.sidecar.model.EntitlementsEvent;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class TokenCacheHandler {

  private final OpenidJwtParserProvider openidJwtParserProvider;

  @ConsumeEvent(value = ENTITLEMENTS_EVENT, blocking = true)
  public void syncCache(EntitlementsEvent event) {
    openidJwtParserProvider.invalidateCache(event.getTenants());
  }
}
