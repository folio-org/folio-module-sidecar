package org.folio.sidecar.integration.keycloak.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.SYSTEM_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quarkus.security.UnauthorizedException;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Consumer;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.sidecar.utils.RoutingUtils;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakTenantFilterTest extends AbstractFilterTest {

  @InjectMocks private KeycloakTenantFilter keycloakTenantFilter;
  @Mock private SidecarProperties sidecarProperties;

  @Test
  void filter_positive() {
    var accessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer(TENANT_NAME));
      when(rc.request().getHeader(TENANT)).thenReturn(TENANT_NAME);
    });

    when(sidecarProperties.isCrossTenantEnabled()).thenReturn(false);

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void filter_positive_accessTokenAndSystemTokenPresent() {
    var accessToken = mock(JsonWebToken.class);
    var systemAccessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemAccessToken);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer(TENANT_NAME));
      when(systemAccessToken.getIssuer()).thenReturn(keycloakIssuer(TENANT_NAME));
      when(rc.request().getHeader(TENANT)).thenReturn(TENANT_NAME);
    });

    when(sidecarProperties.isCrossTenantEnabled()).thenReturn(false);

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void filter_positive_crossTenantEnabled() {
    var accessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer(TENANT_NAME));
    });

    when(sidecarProperties.isCrossTenantEnabled()).thenReturn(true);

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void filter_negative_noResolvedTokens() {
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(TOKEN)).thenReturn(null);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
    });

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Failed to resolve a tenant from token");
  }

  @Test
  void filter_negative_tenantHeaderIsNull() {
    var accessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer(TENANT_NAME));
      when(rc.request().getHeader(TENANT)).thenReturn(null);
    });

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void filter_negative_differentJwtIssuersAndCrossTenantDisabled() {
    var tenant1 = "test-tenant-1";
    var tenant2 = "test-tenant-2";
    var accessToken = mock(JsonWebToken.class);
    var systemAccessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemAccessToken);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer(tenant1));
      when(systemAccessToken.getIssuer()).thenReturn(keycloakIssuer(tenant2));
    });

    when(sidecarProperties.isCrossTenantEnabled()).thenReturn(false);

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Resolved tenant from X-System-Token header is not the same as from X-Okapi-Token");
  }

  @Test
  void filter_negative_differentJwtIssuerAndTenantAndCrossTenantDisabled() {
    var tenant1 = "test-tenant-1";
    var tenant2 = "test-tenant-2";
    var accessToken = mock(JsonWebToken.class);
    var systemAccessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry(), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(systemAccessToken);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer(tenant1));
      when(systemAccessToken.getIssuer()).thenReturn(keycloakIssuer(tenant1));
      when(rc.request().getHeader(TENANT)).thenReturn(tenant2);
    });

    when(sidecarProperties.isCrossTenantEnabled()).thenReturn(false);

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("X-Okapi-Tenant header is not the same as resolved tenant");
  }

  @Test
  void shouldSkip_positive() {
    var routingContext = routingContext(scRoutingEntry(), rc -> {});
    var actual = keycloakTenantFilter.shouldSkip(routingContext);
    assertThat(actual).isFalse();
  }

  @Test
  void shouldSkip_positive_systemRequest() {
    var routingContext = routingContext(scRoutingEntry("system", "foo.item.get"), rc -> {});
    var actual = keycloakTenantFilter.shouldSkip(routingContext);
    assertThat(actual).isTrue();
  }

  @Test
  void shouldSkip_positive_noPermissionsRequired() {
    var routingContext = routingContext(scRoutingEntry("not-system"), rc -> {});
    var actual = keycloakTenantFilter.shouldSkip(routingContext);
    assertThat(actual).isTrue();
  }

  @Test
  void shouldSkip_positive_selfRequest() {
    var routingContext = routingContext(scRoutingEntry("not-system", "foo.item.get"),
      rc -> when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(true));

    var actual = keycloakTenantFilter.shouldSkip(routingContext);

    assertThat(actual).isTrue();
  }

  @Test
  void shouldSkip_positive_notSelfRequest() {
    var routingContext = routingContext(scRoutingEntry("not-system", "foo.item.get"),
      rc -> when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false));

    var actual = keycloakTenantFilter.shouldSkip(routingContext);

    assertThat(actual).isFalse();
  }

  @Test
  void getOrder_positive() {
    var result = keycloakTenantFilter.getOrder();
    assertThat(result).isEqualTo(130);
  }

  @Test
  void shouldSkip_negative_timerEndpoint() {
    var routingContext = routingContext(scRoutingEntryWithId("system", "_timer"), rc -> {});
    var actual = keycloakTenantFilter.shouldSkip(routingContext);
    assertThat(actual).isFalse();
  }

  private static RoutingContext routingContext(ScRoutingEntry routingEntry, Consumer<RoutingContext> rcModifier) {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(RoutingUtils.SC_ROUTING_ENTRY_KEY)).thenReturn(routingEntry);
    rcModifier.accept(routingContext);
    return routingContext;
  }

  private static String keycloakIssuer(String tenant) {
    return "https://keycloak.sample.org/realms/" + tenant;
  }
}
