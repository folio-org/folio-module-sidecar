package org.folio.sidecar.service.routing.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.model.ScRoutingEntry.gatewayRoutingEntry;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.MODULE_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class GatewayRoutingLookupTest {

  private static final String GATEWAY_DESTINATION = "http://gateway:8080";

  @Mock
  private SidecarProperties sidecarProperties;
  private GatewayRoutingLookup gatewayLookup;

  @BeforeEach
  void setUp() {
    when(sidecarProperties.getName()).thenReturn(MODULE_NAME);
    gatewayLookup = new GatewayRoutingLookup(GATEWAY_DESTINATION, sidecarProperties);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void lookupRoute_positive_moduleIdHeaderBlank(String moduleId) {
    var rc = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.MODULE_ID)).thenReturn(moduleId);

    var actual = gatewayLookup.lookupRoute("/foo/entities", rc);

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(Optional.of(gatewayRoutingEntry(GATEWAY_DESTINATION)));
  }

  @Test
  void lookupRoute_positive_moduleIdHeaderNotBlank() {
    var rc = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(rc.request()).thenReturn(request);
    when(request.getHeader(OkapiHeaders.MODULE_ID)).thenReturn(MODULE_ID);

    var actual = gatewayLookup.lookupRoute("/foo/entities", rc);

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEqualTo(Optional.of(gatewayRoutingEntry(GATEWAY_DESTINATION, MODULE_ID)));
  }
}
