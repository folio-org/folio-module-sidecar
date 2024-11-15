package org.folio.sidecar.integration.keycloak;

import static java.util.Collections.emptySet;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;
import org.folio.jwt.openid.OpenidJwtParserProvider;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TokenCacheHandlerTest {

  private static final String TEST_TENANT = "test1";

  @InjectMocks private TokenCacheHandler tokenCacheHandler;
  @Mock private OpenidJwtParserProvider openidJwtParserProvider;

  @Test
  void syncCache_and_getParser_positive_recreateTenant() {
    // enable tenant
    tokenCacheHandler.syncCache(eventForEventBus(TEST_TENANT));

    // re-enable tenant
    tokenCacheHandler.syncCache(eventForEventBus());
    tokenCacheHandler.syncCache(eventForEventBus(TEST_TENANT));

    verify(openidJwtParserProvider).invalidateCache(emptySet());
    verify(openidJwtParserProvider, times(2)).invalidateCache(Set.of(TEST_TENANT));
  }

  private static EntitlementsEvent eventForEventBus(String... tenants) {
    var event = new EntitlementsEvent();
    event.setTenants(Set.of(tenants));
    return event;
  }
}
