package org.folio.sidecar.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.folio.sidecar.support.TestUtils;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TenantEntitlementEventTest {

  @Test
  void deserialize_extractsApplicationId() throws Exception {
    var json = """
      {"type":"ENTITLE","moduleId":"mod-users-19.5.4","tenantName":"diku2",
       "tenantId":"00000000-0000-0000-0000-000000000001",
       "applicationId":"app-platform-minimal-2.0.53"}""";
    var event = TestUtils.OBJECT_MAPPER.readValue(json, TenantEntitlementEvent.class);
    assertThat(event.getApplicationId()).isEqualTo("app-platform-minimal-2.0.53");
  }

  @Test
  void deserialize_applicationIdAbsent_yieldsNull() throws Exception {
    var json = """
      {"type":"REVOKE","moduleId":"mod-users-19.5.4","tenantName":"diku",
       "tenantId":"00000000-0000-0000-0000-000000000002"}""";
    var event = TestUtils.OBJECT_MAPPER.readValue(json, TenantEntitlementEvent.class);
    assertThat(event.getApplicationId()).isNull();
  }

  @Test
  void deserialize_populatesOtherFields() throws Exception {
    var json = """
      {"type":"UPGRADE","moduleId":"mod-foo-1.0.0","tenantName":"test",
       "tenantId":"00000000-0000-0000-0000-000000000003",
       "applicationId":"app-test-1.0.0"}""";
    var event = TestUtils.OBJECT_MAPPER.readValue(json, TenantEntitlementEvent.class);
    assertThat(event.getType()).isEqualTo(TenantEntitlementEvent.Type.UPGRADE);
    assertThat(event.getModuleId()).isEqualTo("mod-foo-1.0.0");
    assertThat(event.getTenantName()).isEqualTo("test");
    assertThat(event.getTenantId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000003"));
    assertThat(event.getApplicationId()).isEqualTo("app-test-1.0.0");
  }
}
