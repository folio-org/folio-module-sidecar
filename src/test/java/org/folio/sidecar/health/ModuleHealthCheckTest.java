package org.folio.sidecar.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.MODULE_HEALTH_PATH;
import static org.folio.sidecar.support.TestConstants.MODULE_NAME;
import static org.folio.sidecar.support.TestConstants.MODULE_URL;
import static org.mockito.Mockito.when;

import org.folio.sidecar.service.PathProcessor;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleHealthCheckTest {

  @Mock private PathProcessor pathProcessor;

  @Test
  void testHealthCheckUrl_positive_withoutPathPrefix() {
    when(pathProcessor.getModulePath(MODULE_HEALTH_PATH)).thenReturn(MODULE_HEALTH_PATH);
    var service = new ModuleHealthCheck(pathProcessor, TestConstants.MODULE_PROPERTIES);

    var result = service.getModuleHealthCheckUrl();

    assertThat(result).isEqualTo(MODULE_URL + MODULE_HEALTH_PATH);
  }

  @Test
  void testHealthCheckUrl_positive_withPathPrefix() {
    var service = new ModuleHealthCheck(pathProcessor, TestConstants.MODULE_PROPERTIES);
    when(pathProcessor.getModulePath(MODULE_HEALTH_PATH)).thenReturn("/" + MODULE_NAME + MODULE_HEALTH_PATH);

    var result = service.getModuleHealthCheckUrl();

    assertThat(result).isEqualTo(MODULE_URL + "/" + MODULE_NAME + MODULE_HEALTH_PATH);
  }
}
