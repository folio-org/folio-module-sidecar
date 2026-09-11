package org.folio.sidecar.it.kafka;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TWO_SECONDS;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.ENTITLE;
import static org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type.UPGRADE;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestJwtGenerator.generateJwtString;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.folio.sidecar.integration.kafka.DiscoveryEvent;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent;
import org.folio.sidecar.integration.kafka.TenantEntitlementEvent.Type;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.service.routing.lookup.TenantModuleResolver;
import org.folio.sidecar.support.TestUtils;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.extensions.InjectWireMock;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Proves that the mod-users-keycloak version is resolved per tenant and kept current from both Kafka channels.
 *
 * <p>WireMock exports one base url for every service, so two tenants cannot point at two different real endpoints:
 * the tenants differ through the {@code location} the {@code am} mappings return. {@code testtenant} resolves to the
 * WireMock base url, where mod-users-keycloak is actually stubbed, and {@code another-tenant} to a literal address,
 * which is what makes the per-tenant assertions decisive. That the resolved location is really used is proven
 * separately by the cross-tenant request test, which reaches mod-users-keycloak over HTTP.</p>
 */
@IntegrationTest
@EnableWireMock
@TestProfile(TenantModuleTargetIT.CrossTenantMessagingProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "entitlement"),
  @ResourceArg(value = "incoming", name = "discovery")
})
class TenantModuleTargetIT {

  private static final String MOD_USERS_KEYCLOAK = "mod-users-keycloak";
  private static final String OTHER_TENANT = "another-tenant";
  private static final String ID_3 = "mod-users-keycloak-3.0.13";
  private static final String ID_4 = "mod-users-keycloak-4.0.2";
  private static final String LOCATION_4 = "http://sc-users-keycloak-4:8081";
  private static final String REDEPLOYED_LOCATION_4 = "http://sc-users-keycloak-4b:8081";
  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";

  @ConfigProperty(name = "keycloak.url") String keycloakUrl;
  @InjectWireMock WireMockServer wireMockServer;
  @Inject TenantModuleResolver resolver;
  @Inject @Any InMemoryConnector connector;

  /**
   * Application-scoped state survives between tests in the class, so every test that moved the second tenant puts it
   * back on its own version through an entitlement event, and any entitlements override is dropped.
   */
  @AfterEach
  void restoreSecondTenant() {
    removeStubsFor("/entitlements");
    sendEntitlementEvent(ID_4, OTHER_TENANT, UPGRADE);
  }

  /** A sidecar started after onboarding resolves the tenant lazily, without an earlier Kafka event. */
  @Test
  void startup_positive_resolvesTenantOnFirstUse() {
    awaitTarget(TENANT_NAME, ID_3, wireMockServer.baseUrl());
  }

  /** AC1: a second tenant on a different version resolves to a different address. */
  @Test
  void newTenant_positive_resolvesToItsOwnVersion() {
    enableSecondTenant();

    awaitTarget(OTHER_TENANT, ID_4, LOCATION_4);
    awaitTarget(TENANT_NAME, ID_3, wireMockServer.baseUrl());
  }

  /**
   * AC2: an upgrade moves one tenant and leaves the other alone.
   *
   * <p>The entitlements stub deliberately still answers with the previous version while the event is handled. That
   * is what mgr-tenant-entitlements really does: module events are published inside the module flow, and the
   * application entitlement the API reads from is swapped only by the later application finalizer. Re-reading the
   * API here would therefore return the old version, so the module id from the event payload is what must win.</p>
   *
   * <p>Moving the stub afterwards only checks that the two sources agreeing causes no flap. Convergence after an
   * event that never arrives needs a controllable clock and is covered by {@code TenantModuleResolverTest}.</p>
   */
  @Test
  void entitlementEvent_positive_upgradeMovesOnlyThatTenant() {
    enableSecondTenant();
    awaitTarget(OTHER_TENANT, ID_4, LOCATION_4);

    sendEntitlementEvent(ID_3, OTHER_TENANT, UPGRADE);

    awaitTarget(OTHER_TENANT, ID_3, wireMockServer.baseUrl());
    awaitTarget(TENANT_NAME, ID_3, wireMockServer.baseUrl());

    overrideEntitlementsFor(OTHER_TENANT, ID_3);
    await().pollDelay(TWO_SECONDS).atMost(Duration.ofSeconds(15))
      .untilAsserted(() -> assertTarget(OTHER_TENANT, ID_3, wireMockServer.baseUrl()));
  }

  /**
   * AC3: the same version redeployed at a new address is picked up, with no entitlement change.
   *
   * <p>This also covers the shared discovery updater: the profile leaves {@code routing.dynamic.enabled} at its
   * default {@code false}, so a discovery event reaching this path proves the updater is no longer bound to that
   * flag.</p>
   */
  @Test
  void discoveryEvent_positive_picksUpNewAddressForSameVersion() {
    enableSecondTenant();
    awaitTarget(OTHER_TENANT, ID_4, LOCATION_4);

    redeployModuleTo(REDEPLOYED_LOCATION_4);

    awaitTarget(OTHER_TENANT, ID_4, REDEPLOYED_LOCATION_4);
    awaitTarget(TENANT_NAME, ID_3, wireMockServer.baseUrl());
    redeployModuleTo(LOCATION_4);
    awaitTarget(OTHER_TENANT, ID_4, LOCATION_4);
  }

  /**
   * AC6: a cross-tenant request runs ingress filter 150, which calls mod-users-keycloak over HTTP at the resolved
   * address, and a warm target does not require a manager lookup while the request is handled.
   */
  @Test
  void crossTenantRequest_positive_makesNoManagerCallOnRequestPath() {
    awaitTarget(TENANT_NAME, ID_3, wireMockServer.baseUrl());
    var mockId = addObtainTokenRequestToWiremock();
    wireMockServer.resetRequests();

    TestUtils.givenJson()
      .header(OkapiHeaders.TENANT, TENANT_NAME)
      .header(OkapiHeaders.AUTHORIZATION, "Bearer " + generateJwtString(keycloakUrl, OTHER_TENANT, USER_ID))
      .get("/foo/entities")
      .then()
      .statusCode(SC_OK);
    wireMockServer.removeStubMapping(mockId);

    assertThat(wireMockServer.findAll(getRequestedFor(urlPathMatching("/(entitlements|modules).*")))).isEmpty();
  }

  /** Enables the second tenant the way the platform does - an entitlement event for this sidecar's own module. */
  private void enableSecondTenant() {
    sendEntitlementEvent(MODULE_ID, OTHER_TENANT, ENTITLE);
  }

  private void sendEntitlementEvent(String moduleId, String tenant, Type type) {
    connector.<TenantEntitlementEvent>source("entitlement")
      .send(TenantEntitlementEvent.of(moduleId, tenant, UUID.randomUUID(), type));
  }

  private void sendDiscoveryEvent() {
    connector.<DiscoveryEvent>source("discovery").send(DiscoveryEvent.of(ID_4));
  }

  private void redeployModuleTo(String location) {
    removeStubsFor("/modules/" + ID_4 + "/discovery");
    addDiscoveryMapping(location);
    sendDiscoveryEvent();
  }

  private void awaitTarget(String tenant, String moduleId, String location) {
    await().atMost(Duration.ofSeconds(15)).pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertTarget(tenant, moduleId, location));
  }

  private void assertTarget(String tenant, String moduleId, String location) {
    var discovery = resolver.resolve(tenant, MOD_USERS_KEYCLOAK).result();
    assertThat(discovery).isNotNull();
    assertThat(discovery.getId()).isEqualTo(moduleId);
    assertThat(discovery.getLocation()).isEqualTo(location);
  }

  private void removeStubsFor(String urlPath) {
    wireMockServer.getStubMappings().stream()
      .filter(stub -> urlPath.equals(stub.getRequest().getUrlPath()))
      .filter(stub -> stub.getPriority() != null)
      .toList()
      .forEach(wireMockServer::removeStubMapping);
  }

  private void addDiscoveryMapping(String location) {
    var json = """
      {
        "priority": 1,
        "request": {"method": "GET", "urlPath": "/modules/%s/discovery"},
        "response": {
          "status": 200,
          "headers": {"Content-Type": "application/json"},
          "jsonBody": {"location": "%s", "id": "%s", "name": "mod-users-keycloak", "version": "4.0.2"}
        }
      }
      """.formatted(ID_4, location, ID_4);
    wireMockServer.addStubMapping(StubMapping.buildFrom(json));
  }

  /** Moves the entitlements API to another version, the way the application flow finalizer eventually does. */
  private void overrideEntitlementsFor(String tenant, String moduleId) {
    var json = """
      {
        "priority": 1,
        "request": {
          "method": "GET",
          "urlPath": "/entitlements",
          "queryParameters": {"tenant": {"equalTo": "%s"}, "includeModules": {"equalTo": "true"}}
        },
        "response": {
          "status": 200,
          "headers": {"Content-Type": "application/json"},
          "jsonBody": {
            "totalRecords": 1,
            "entitlements": [{"applicationId": "application-0.0.1", "modules": ["mod-foo-0.2.1", "%s"]}]
          }
        }
      }
      """.formatted(tenant, moduleId);
    wireMockServer.addStubMapping(StubMapping.buildFrom(json));
  }

  private UUID addObtainTokenRequestToWiremock() {
    var id = UUID.randomUUID();
    var sessionState = UUID.randomUUID();
    var accessToken = generateJwtString(keycloakUrl, TENANT_NAME, USER_ID, sessionState);
    var mappingsTemplate = TestUtils.readString("/mappings-templates/keycloak/obtain-token-template.json");

    var obtainTokenRequestStubMappingJson = mappingsTemplate
      .replace("{{id}}", id.toString())
      .replace("{{accessToken}}", accessToken)
      .replace("{{refreshToken}}", accessToken)
      .replace("{{tenant}}", TENANT_NAME)
      .replace("{{sessionState}}", sessionState.toString());

    var stubMapping = StubMapping.buildFrom(obtainTokenRequestStubMappingJson);
    wireMockServer.addStubMapping(stubMapping);

    return stubMapping.getId();
  }

  public static final class CrossTenantMessagingProfile extends InMemoryMessagingTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      var result = new HashMap<>(super.getConfigOverrides());

      result.put("sidecar.cross-tenant.enabled", "true");

      return result;
    }
  }
}
