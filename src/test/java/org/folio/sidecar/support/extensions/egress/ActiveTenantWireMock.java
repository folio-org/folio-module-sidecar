package org.folio.sidecar.support.extensions.egress;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario: one active tenant (testtenant) whose entitlements include this module,
 * and the AM egress bootstrap endpoint returns found=true with required modules.
 * Expected result: EgressRoutingLookup has a table for testtenant.
 */
public class ActiveTenantWireMock extends EgressWireMockBase {

  @Override
  protected void addScenarioStubs(WireMockServer wm) {
    // MTE: getModuleEntitlements — used by TenantService to discover which tenants are active.
    // Returns tenantId so TenantService can look up the tenant name from TM.
    stub(wm, get(urlPathEqualTo("/entitlements/modules/" + MODULE_ID))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .withHeader("Content-Type", equalTo("application/json"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""
          {"totalRecords":1,"entitlements":[{"applicationId":"%s","tenantId":"%s"}]}
          """.formatted(APP_ID, TENANT_ID))));

    // MTE: getAllTenantEntitlements — used by TenantEgressRoutingService.doRefreshTenant.
    // Must include this module's ID for isModuleActive() to return true.
    stub(wm, get(urlPathEqualTo("/entitlements"))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(TE_ENTITLEMENTS_WITH_MODULE_BODY)));

    // AM: POST /modules/{moduleId}/bootstrap (body type==egress).
    // Returns found=true so TenantEgressRoutingService builds the route table.
    // The ingress POST (type==ingress) is served by EgressWireMockBase.
    stub(wm, post(urlPathEqualTo("/modules/" + MODULE_ID + "/bootstrap"))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(matchingJsonPath("$[?(@.type == 'egress')]"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(AM_EGRESS_BOOTSTRAP_FOUND_BODY)));
  }
}
