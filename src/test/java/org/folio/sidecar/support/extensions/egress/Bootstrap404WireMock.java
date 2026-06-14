package org.folio.sidecar.support.extensions.egress;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario: active tenant, but the AM egress bootstrap endpoint returns 404 (not yet deployed).
 * TenantEgressRoutingService treats 404 as "endpoint missing" and skips scoped egress gracefully.
 * Expected result: startup succeeds AND EgressRoutingLookup has NO table for any tenant.
 */
public class Bootstrap404WireMock extends EgressWireMockBase {

  @Override
  protected void addScenarioStubs(WireMockServer wm) {
    // MTE: getModuleEntitlements — active tenant
    stub(wm, get(urlPathEqualTo("/entitlements/modules/" + MODULE_ID))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .withHeader("Content-Type", equalTo("application/json"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""
          {"totalRecords":1,"entitlements":[{"applicationId":"%s","tenantId":"%s"}]}
          """.formatted(APP_ID, TENANT_ID))));

    // MTE: getAllTenantEntitlements — returns module so isModuleActive() is true
    stub(wm, get(urlPathEqualTo("/entitlements"))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(TE_ENTITLEMENTS_WITH_MODULE_BODY)));

    // AM: POST /modules/{moduleId}/bootstrap — returns 404 to simulate endpoint not deployed
    stub(wm, post(urlPathEqualTo("/modules/" + MODULE_ID + "/bootstrap"))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .withHeader("Content-Type", equalTo("application/json"))
      .willReturn(aResponse()
        .withStatus(404)
        .withHeader("Content-Type", "application/json")
        .withBody("""
          {"error":"Not Found"}
          """)));
  }
}
