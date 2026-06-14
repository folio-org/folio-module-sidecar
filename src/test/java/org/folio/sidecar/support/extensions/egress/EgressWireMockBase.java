package org.folio.sidecar.support.extensions.egress;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.support.extensions.InjectWireMock;

/**
 * Base for scenario-specific WireMock resources used in egress startup ITs.
 *
 * <p>Starts an isolated WireMock server with a minimal set of stubs that allow Quarkus to boot
 * (KC admin token, JWKS certs, MTE module-entitlements, TM tenants, AM ingress bootstrap POST).
 * The ingress bootstrap is served via POST /modules/{id}/bootstrap matched on body type==ingress,
 * so startup ingress always succeeds. Subclasses override {@link #addScenarioStubs(WireMockServer)}
 * to layer scenario-specific stubs (egress POST matched on body type==egress): e.g. different MTE
 * response to simulate no-tenant, or 404 on the egress POST /bootstrap.
 */
@Log4j2
abstract class EgressWireMockBase implements QuarkusTestResourceLifecycleManager {

  protected static final String MODULE_ID = "mod-foo-0.2.1";
  protected static final String TENANT_NAME = "testtenant";
  protected static final String TENANT_ID = "90b113f0-4e98-45f7-bccc-cf318e13a9bc";
  protected static final String APP_ID = "application-0.0.1";

  /** The admin token returned by the KC stub — must match what the real client expects. */
  protected static final String ADMIN_TOKEN = "dGVzdC1hY2Nlc3MtdG9rZW4=";

  // RSA public key modulus (base64url) used in the JWKS stub below.
  private static final String RSA_N = "mcCDCn7e7eFlpshZxPeQjzuXFUc5bQnn6tPtTaOt-A1fftoZYdJ7-5wlNv-6sUMG5L4u"
    + "RiGXR9yfq-_Pc88hX_7yXE-jGA8ng714Hk4VQNSBxbvn-sKHzxbxNZUz7Rz0tuciosEdwVpmwS5hK0jlBsBetYkx4B-czs6qrT1"
    + "uqgEgwNDQ8rweEreCjMUP4tm6B7yw20oXKDFws995IyTTxaMNkMtz1AKaOVj6HEAcVDvqr7lNUxDWEJkAOgYVMVl2XT3P0IMMck"
    + "d-EXGqQvNMS9DnRG8qVv2zHUq1DbPbOayx431ERZtnVXmCQFs0-x7kwPwpQ_rNnh_dnGOSyLRJYw";

  protected static final String KEYCLOAK_CERTS_BODY = """
    {"keys":[{
      "kid":"qJr6ysS_hauNBc65Sp16ORFOqJtII3ej6uAP2-jOnuo","kty":"RSA","alg":"RS256","use":"sig",
      "n":"%s",
      "e":"AQAB"
    }]}
    """.formatted(RSA_N);

  protected static final String TE_ENTITLEMENTS_WITH_MODULE_BODY = """
    {"totalRecords":1,"entitlements":[{
      "applicationId":"%s","tenantId":"%s",
      "modules":["%s"]
    }]}
    """.formatted(APP_ID, TENANT_ID, MODULE_ID);

  protected static final String AM_INGRESS_BOOTSTRAP_BODY = """
    {"ingress":{
      "module":{"moduleId":"%s","applicationId":"%s","location":"http://sc-foo:8081",
        "interfaces":[{"id":"foo","version":"0.1","endpoints":[
          {"methods":["GET"],"pathPattern":"/foo/entities",
           "permissionsRequired":["foo.entities.collection.get"]},
          {"methods":["POST"],"pathPattern":"/_/tenant"}
        ]}]
      },"requiredModules":[]}
    }
    """.formatted(MODULE_ID, APP_ID);

  protected static final String AM_EGRESS_BOOTSTRAP_FOUND_BODY = """
    {"egress":{"found":true,"bootstrap":{
      "module":{"moduleId":"%s","applicationId":"%s","location":"http://sc-foo:8081","interfaces":[]},
      "requiredModules":[
        {"moduleId":"mod-bar-0.5.1","applicationId":"%s","location":"http://mod-bar:8081",
         "interfaces":[{"id":"bar","version":"0.1","endpoints":[
           {"methods":["GET"],"pathPattern":"/bar/entities"}
         ]}]}
      ]
    }}}
    """.formatted(MODULE_ID, APP_ID, APP_ID);

  @Getter
  private WireMockServer server;

  @Override
  public Map<String, String> start() {
    var config = WireMockConfiguration.options()
      .dynamicPort()
      .globalTemplating(true)
      .notifier(new Slf4jNotifier(false));

    server = new WireMockServer(config);
    server.start();

    addBaseStubs(server);
    addScenarioStubs(server);

    var url = server.baseUrl();
    log.info("Egress IT WireMock [{}] started at: {}", getClass().getSimpleName(), url);
    return Map.of(
      "MODULE_URL", url,
      "AM_CLIENT_URL", url,
      "TE_CLIENT_URL", url,
      "TM_CLIENT_URL", url,
      "KC_URL", url,
      "MOD_USERS_KEYCLOAK_URL", url
    );
  }

  @Override
  public void stop() {
    if (server != null) {
      server.stop();
      log.info("Egress IT WireMock [{}] stopped", getClass().getSimpleName());
      server = null;
    }
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(server,
      new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
  }

  /** Override to add scenario-specific stubs before Quarkus boots. */
  protected abstract void addScenarioStubs(WireMockServer wm);

  // ---- shared base stubs ----

  private static void addBaseStubs(WireMockServer wm) {
    // Keycloak admin token
    stub(wm, post(urlPathMatching("/realms/master/protocol/openid-connect/token"))
      .withRequestBody(containing("grant_type=client_credentials"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""
          {"access_token":"%s","expires_in":1800,"token_type":"Bearer"}
          """.formatted(ADMIN_TOKEN))));

    // Keycloak JWKS (certs) — needed for JWT validation during request handling
    stub(wm, get(urlPathMatching("/realms/.*/protocol/openid-connect/certs"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(KEYCLOAK_CERTS_BODY)));

    // AM ingress bootstrap POST (body type==ingress) — needed by RoutingService.init() at startup.
    // Disambiguated from the egress POST (type==egress) by the request-body JSONPath matcher, so
    // ingress always returns 200 even in scenarios where egress is missing or 404s.
    stub(wm, post(urlPathEqualTo("/modules/" + MODULE_ID + "/bootstrap"))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(matchingJsonPath("$[?(@.type == 'ingress')]"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(AM_INGRESS_BOOTSTRAP_BODY)));

    // TM tenants — returns testtenant when queried by ID
    stub(wm, get(urlPathEqualTo("/tenants"))
      .withHeader("X-Okapi-Token", equalTo(ADMIN_TOKEN))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""
          {"totalRecords":1,"tenants":[{"id":"%s","name":"%s","description":"test description"}]}
          """.formatted(TENANT_ID, TENANT_NAME))));
  }

  // ---- common stub helper ----

  protected static void stub(WireMockServer wm, MappingBuilder builder) {
    wm.addStubMapping(builder.build());
  }
}
