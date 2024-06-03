package org.folio.sidecar.it;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.net.ConnectException;
import org.folio.sidecar.integration.am.ApplicationManagerClient;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(CommonIntegrationTestProfile.class)
@EnableWireMock
class ApplicationManagerServiceIT {

  @InjectMock ApplicationManagerClient amClient;
  @Inject ApplicationManagerService service;

  @Test
  void getModuleBootstrap_positive_applyRetries() {
    when(amClient.getModuleBootstrap(any(), any()))
      .thenReturn(failedFuture(new ConnectException("error")))
      .thenReturn(succeededFuture(TestConstants.MODULE_BOOTSTRAP));

    var bootstrap = service.getModuleBootstrap();

    await().atMost(ofSeconds(5)).until(bootstrap::isComplete);

    assertTrue(bootstrap.succeeded());
    Assertions.assertEquals(TestConstants.MODULE_BOOTSTRAP, bootstrap.result());

    verify(amClient, times(2)).getModuleBootstrap(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN);
  }

  @Test
  void getModuleBootstrap_negative_retriesFailed() {
    when(amClient.getModuleBootstrap(any(), any()))
      .thenReturn(failedFuture(new ConnectException("error")));

    var bootstrap = service.getModuleBootstrap();

    await().atMost(ofSeconds(5)).until(bootstrap::isComplete);

    assertTrue(bootstrap.failed());
    verify(amClient, times(3)).getModuleBootstrap(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN);
  }
}
