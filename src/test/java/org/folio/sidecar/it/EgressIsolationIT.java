package org.folio.sidecar.it;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Set;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint;
import org.folio.sidecar.integration.am.model.ModuleBootstrapInterface;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.service.routing.lookup.EgressRoutingLookup;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying that cross-application egress routing is isolated per tenant.
 *
 * <p>Scenario: two applications share the same path ({@code /users/entities}) but provide it
 * through different module versions:
 * <ul>
 *   <li>{@code app-platform-minimal-2.0.53} → {@code mod-users-19.5.4} at
 *       {@code http://mod-users-19-5-4:8081}</li>
 *   <li>{@code app-platform-complete-1.2.0} → {@code mod-users-19.6.0} at
 *       {@code http://mod-users-19-6-0:8081}</li>
 * </ul>
 *
 * <p>Tenant {@code diku2} is entitled only to {@code app-platform-minimal-2.0.53}, so its egress
 * requests MUST resolve to {@code mod-users-19.5.4}. Tenant {@code diku-other} is entitled only to
 * {@code app-platform-complete-1.2.0}, so its egress requests MUST resolve to
 * {@code mod-users-19.6.0}.
 */
@IntegrationTest
@TestProfile(InMemoryMessagingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "entitlement")
})
class EgressIsolationIT {

  private static final String APP_MINIMAL = "app-platform-minimal-2.0.53";
  private static final String APP_COMPLETE = "app-platform-complete-1.2.0";

  private static final String MOD_USERS_OLD_ID = "mod-users-19.5.4";
  private static final String MOD_USERS_OLD_URL = "http://mod-users-19-5-4:8081";
  private static final String MOD_USERS_NEW_ID = "mod-users-19.6.0";
  private static final String MOD_USERS_NEW_URL = "http://mod-users-19-6-0:8081";

  private static final String TENANT_MINIMAL = "diku2";
  private static final String TENANT_COMPLETE = "diku-other";

  private static final String USERS_PATH = "/users/entities";
  private static final String USERS_INTERFACE = "users";

  @InjectSpy
  EgressRoutingLookup egressRoutingLookup;

  @InjectMock
  TenantService tenantService;

  @BeforeEach
  void setUp() {
    // Bootstrap app-platform-minimal-2.0.53 with mod-users-19.5.4
    egressRoutingLookup.onApplicationBootstrap(APP_MINIMAL,
      List.of(discovery(MOD_USERS_OLD_ID, MOD_USERS_OLD_URL, APP_MINIMAL, USERS_INTERFACE, USERS_PATH,
        List.of("GET", "POST"))));

    // Bootstrap app-platform-complete-1.2.0 with mod-users-19.6.0
    egressRoutingLookup.onApplicationBootstrap(APP_COMPLETE,
      List.of(discovery(MOD_USERS_NEW_ID, MOD_USERS_NEW_URL, APP_COMPLETE, USERS_INTERFACE, USERS_PATH,
        List.of("GET", "POST"))));

    // diku2 is entitled only to the minimal app
    when(tenantService.getApplicationIds(TENANT_MINIMAL)).thenReturn(Set.of(APP_MINIMAL));

    // diku-other is entitled only to the complete app
    when(tenantService.getApplicationIds(TENANT_COMPLETE)).thenReturn(Set.of(APP_COMPLETE));
  }

  @Test
  void lookupRoute_diku2_resolvesToOldModUsers() {
    var rc = routingContext(GET, TENANT_MINIMAL);

    var result = egressRoutingLookup.lookupRoute(USERS_PATH, rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isPresent();
    var entry = result.result().get();
    assertThat(entry.getModuleId())
      .as("diku2 must route to mod-users-19.5.4, not mod-users-19.6.0")
      .isEqualTo(MOD_USERS_OLD_ID);
    assertThat(entry.getLocation()).isEqualTo(MOD_USERS_OLD_URL);
  }

  @Test
  void lookupRoute_dikuOther_resolvesToNewModUsers() {
    var rc = routingContext(GET, TENANT_COMPLETE);

    var result = egressRoutingLookup.lookupRoute(USERS_PATH, rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isPresent();
    var entry = result.result().get();
    assertThat(entry.getModuleId())
      .as("diku-other must route to mod-users-19.6.0, not mod-users-19.5.4")
      .isEqualTo(MOD_USERS_NEW_ID);
    assertThat(entry.getLocation()).isEqualTo(MOD_USERS_NEW_URL);
  }

  @Test
  void lookupRoute_diku2_doesNotSeeCompleteAppRoutes() {
    // seed a route that only exists in app-complete
    var completePath = "/complete-only/items";
    egressRoutingLookup.onApplicationBootstrap(APP_COMPLETE,
      List.of(discovery(MOD_USERS_NEW_ID, MOD_USERS_NEW_URL, APP_COMPLETE, "complete-iface",
        completePath, List.of("GET"))));

    var rc = routingContext(GET, TENANT_MINIMAL);
    var result = egressRoutingLookup.lookupRoute(completePath, rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result())
      .as("diku2 must not see routes from app-platform-complete-1.2.0")
      .isEmpty();
  }

  @Test
  void lookupRoute_dikuOther_doesNotSeeMinimalAppRoutes() {
    // seed a route that only exists in app-minimal
    var minimalPath = "/minimal-only/records";
    egressRoutingLookup.onApplicationBootstrap(APP_MINIMAL,
      List.of(discovery(MOD_USERS_OLD_ID, MOD_USERS_OLD_URL, APP_MINIMAL, "minimal-iface",
        minimalPath, List.of("POST"))));

    var rc = routingContext(POST, TENANT_COMPLETE);
    var result = egressRoutingLookup.lookupRoute(minimalPath, rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result())
      .as("diku-other must not see routes from app-platform-minimal-2.0.53")
      .isEmpty();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static RoutingContext routingContext(io.vertx.core.http.HttpMethod method, String tenant) {
    var rc = mock(RoutingContext.class);
    var req = mock(HttpServerRequest.class);
    when(rc.request()).thenReturn(req);
    when(req.method()).thenReturn(method);
    lenient().when(req.getHeader(OkapiHeaders.TENANT)).thenReturn(tenant);
    lenient().when(req.getHeader(OkapiHeaders.MODULE_ID)).thenReturn(null);
    return rc;
  }

  private static ModuleBootstrapDiscovery discovery(String moduleId, String location,
    String applicationId, String interfaceId, String pathPattern, List<String> methods) {
    var endpoint = new ModuleBootstrapEndpoint();
    endpoint.setPathPattern(pathPattern);
    endpoint.setMethods(methods.toArray(new String[0]));

    var iface = new ModuleBootstrapInterface();
    iface.setId(interfaceId);
    iface.setEndpoints(List.of(endpoint));

    var d = new ModuleBootstrapDiscovery();
    d.setModuleId(moduleId);
    d.setLocation(location);
    d.setApplicationId(applicationId);
    d.setInterfaces(List.of(iface));
    return d;
  }
}
