package org.folio.sidecar.support.extensions.egress;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario: no active tenants — the MTE module-entitlements endpoint returns an empty list.
 * TenantService will have an empty enabled-tenants set; TenantEgressRoutingService.init() skips.
 * Startup ingress still succeeds via the ingress POST (type==ingress) served by EgressWireMockBase;
 * no egress POST is made because there are no active tenants.
 * Expected result: EgressRoutingLookup has NO table for any tenant.
 */
public class NoActiveTenantWireMock extends EgressWireMockBase {

  @Override
  protected void addScenarioStubs(WireMockServer wm) {
    // MTE: getModuleEntitlements — returns empty, so TenantService learns no tenants are active.
    stub(wm, get(urlPathEqualTo("/entitlements/modules/" + MODULE_ID))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .withHeader("Content-Type", equalTo("application/json"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""
          {"totalRecords":0,"entitlements":[]}
          """)));

    // MTE: getAllTenantEntitlements — not expected to be called (no tenants), but stub for safety.
    stub(wm, get(urlPathEqualTo("/entitlements"))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""
          {"totalRecords":0,"entitlements":[]}
          """)));
  }
}
