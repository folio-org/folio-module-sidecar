package org.folio.sidecar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleHealthCheckTest {

  private static final String SIDECAR_NAME = "sc-test";
  private static final String SIDECAR_URL = "http://sc-test";

  @Test
  void testHealthCheckUrl_positive_withoutPathPrefix() {
    var sidecarProperties = sidecarProperties(false);
    var service = new ModuleHealthCheck(TestConstants.MODULE_PROPERTIES, sidecarProperties);

    var result = service.getModuleHealthCheckUrl();

    assertThat(result).isEqualTo(TestConstants.MODULE_URL + TestConstants.MODULE_HEALTH_PATH);
  }

  @Test
  void testHealthCheckUrl_positive_withPathPrefix() {
    var sidecarProperties = sidecarProperties(true);
    var service = new ModuleHealthCheck(TestConstants.MODULE_PROPERTIES, sidecarProperties);

    var result = service.getModuleHealthCheckUrl();

    assertThat(result).isEqualTo(
      TestConstants.MODULE_URL + "/" + TestConstants.MODULE_NAME + TestConstants.MODULE_HEALTH_PATH);
  }

  private static SidecarProperties sidecarProperties(boolean modulePrefixEnabled) {
    var sidecarProperties = new SidecarProperties();
    sidecarProperties.setName(SIDECAR_NAME);
    sidecarProperties.setUrl(SIDECAR_URL);
    sidecarProperties.setModuleHealthPathPrefixEnabled(modulePrefixEnabled);
    return sidecarProperties;
  }
}
