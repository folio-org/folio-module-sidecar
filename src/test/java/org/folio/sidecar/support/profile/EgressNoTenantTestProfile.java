package org.folio.sidecar.support.profile;

/**
 * Test profile for TenantEgressNoTenantStartupIT (no active tenants scenario).
 * Distinct class identity forces Quarkus to restart the application for this test class.
 */
public class EgressNoTenantTestProfile extends CommonIntegrationTestProfile {
}
