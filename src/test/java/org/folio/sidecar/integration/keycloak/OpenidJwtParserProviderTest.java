package org.folio.sidecar.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OpenidJwtParserProviderTest {

  private static final String TEST_TENANT = "test1";
  private static final String ISSUER_URI = "http://kc:8081/test1";

  @InjectMocks private OpenidJwtParserProvider provider;

  @Test
  void syncCache_and_getParser_positive_recreateTenant() {
    // enable tenant
    provider.syncCache(eventForEventBus(TEST_TENANT));

    final var parser1 = provider.getParser(ISSUER_URI);

    // re-enable tenant
    provider.syncCache(eventForEventBus());
    provider.syncCache(eventForEventBus(TEST_TENANT));

    var parser2 = provider.getParser(ISSUER_URI);

    assertThat(parser1).isNotSameAs(parser2);
  }

  @Test
  void getParser_positive_cached() {
    var created = provider.getParser(ISSUER_URI);
    var cached = provider.getParser(ISSUER_URI);
    assertThat(cached).isSameAs(created);
  }

  private static EntitlementsEvent eventForEventBus(String... tenants) {
    var event = new EntitlementsEvent();
    event.setTenants(Set.of(tenants));
    return event;
  }
}
