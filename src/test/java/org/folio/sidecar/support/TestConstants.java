package org.folio.sidecar.support;

import static org.folio.sidecar.support.TestUtils.parse;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.integration.am.AppManagerClientProperties;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.keycloak.model.TokenResponse;
import org.folio.sidecar.integration.te.TenantEntitlementClientProperties;
import org.folio.sidecar.integration.tm.TenantManagerClientProperties;
import org.folio.sidecar.model.ClientCredentials;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final String TENANT_NAME = "testtenant";
  public static final String FOLIO_ENV = "folio";
  public static final String TENANT_ID = "90b113f0-4e98-45f7-bccc-cf318e13a9bc";
  public static final String USER_ID = "00000000-0000-0000-0000-111111111111";
  public static final UUID TENANT_UUID = UUID.fromString(TENANT_ID);
  public static final String MODULE_NAME = "mod-foo";
  public static final String SIDECAR_NAME = "sc-mod-foo";
  public static final String GATEWAY_URL = "http://api-gateway:8000";
  public static final String MODULE_VERSION = "0.2.1";
  public static final String MODULE_ID = MODULE_NAME + "-" + MODULE_VERSION;
  public static final String MODULE_URL = "http://sc-foo:8081";
  public static final String MODULE_HEALTH_PATH = "/admin/health";
  public static final String APPLICATION_ID = "application-0.0.1";
  public static final ModuleBootstrap MODULE_BOOTSTRAP =
    parse(TestUtils.readString("json/module-bootstrap.json"), ModuleBootstrap.class);
  public static final String AUTH_TOKEN = "dGVzdC1hY2Nlc3MtdG9rZW4=";
  public static final String REFRESH_TOKEN = "dGVzdC1yZWZyZXNoLXRva2Vu";
  public static final String SYS_TOKEN = "dGVzdC1zeXN0ZW0tYWNjZXNzLXRva2Vu";
  public static final TokenResponse TOKEN_RESPONSE = new TokenResponse(AUTH_TOKEN, REFRESH_TOKEN, 10L);
  public static final ClientCredentials
    LOGIN_CLIENT_CREDENTIALS = ClientCredentials.of(TENANT_NAME + "-login-app", "secret");

  public static final ModuleProperties MODULE_PROPERTIES =
    new ModuleProperties(MODULE_ID, MODULE_NAME, MODULE_VERSION, MODULE_URL, MODULE_HEALTH_PATH);

  public static final AppManagerClientProperties AM_PROPERTIES =
    new AppManagerClientProperties("http://am:8081");

  public static final TenantEntitlementClientProperties TE_PROPERTIES =
    new TenantEntitlementClientProperties("http://te:8081", 500);

  public static final TenantManagerClientProperties TM_PROPERTIES =
    TenantManagerClientProperties.of("http://tm:8081", 50);

  public static final String SIDECAR_SIGNATURE_HEADER = "x-okapi-sidecar-signature";
}
