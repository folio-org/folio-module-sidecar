package org.folio.sidecar.it;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.net.ConnectException;
import lombok.SneakyThrows;
import org.folio.sidecar.integration.am.ApplicationManagerClient;
import org.folio.sidecar.integration.am.ApplicationManagerService;
import org.folio.sidecar.support.TestConstants;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.profile.CommonIntegrationTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
@EnableWireMock
@TestProfile(CommonIntegrationTestProfile.class)
class ApplicationManagerServiceIT {

  @InjectMock ApplicationManagerClient amClient;
  @Inject ApplicationManagerService service;

  @BeforeAll
  @SneakyThrows
  static void beforeAll() {
    Thread.sleep(2000);
  }

  @Test
  void getModuleBootstrap_positive_applyRetries() {
    when(amClient.getModuleBootstrap(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
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
    when(amClient.getModuleBootstrap(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN))
      .thenReturn(failedFuture(new ConnectException("error")));

    var bootstrap = service.getModuleBootstrap();

    await().atMost(ofSeconds(5)).until(bootstrap::isComplete);

    assertTrue(bootstrap.failed());
    verify(amClient, atLeast(2)).getModuleBootstrap(TestConstants.MODULE_ID, TestConstants.AUTH_TOKEN);
  }
}
