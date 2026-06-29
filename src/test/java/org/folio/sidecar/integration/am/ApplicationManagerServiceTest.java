package org.folio.sidecar.integration.am;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.MODULE_PROPERTIES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.function.Supplier;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.service.RetryTemplate;
import org.folio.sidecar.service.token.ServiceTokenProvider;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationManagerServiceTest {

  @Mock private ServiceTokenProvider tokenProvider;
  @Mock private RetryTemplate retryTemplate;
  @Mock private ApplicationManagerClient appManagerClient;

  private ApplicationManagerService service;

  @BeforeEach
  void setUp() {
    service = new ApplicationManagerService(tokenProvider, retryTemplate, appManagerClient, MODULE_PROPERTIES);

    when(retryTemplate.callAsync(any(Supplier.class))).thenAnswer(invocation -> {
      Supplier<Future<ModuleBootstrap>> supplier = invocation.getArgument(0);
      return supplier.get();
    });
  }

  @Test
  void getModuleBootstrap_positive() {
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(AUTH_TOKEN));
    when(appManagerClient.getModuleBootstrap(MODULE_ID, AUTH_TOKEN)).thenReturn(succeededFuture(MODULE_BOOTSTRAP));

    var actual = service.getModuleBootstrap();

    assertThat(actual.result()).isEqualTo(MODULE_BOOTSTRAP);
  }

  @Test
  void getIngressBootstrap_positive() {
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(AUTH_TOKEN));
    when(appManagerClient.getIngressBootstrap(MODULE_ID, AUTH_TOKEN)).thenReturn(succeededFuture(MODULE_BOOTSTRAP));

    var actual = service.getIngressBootstrap();

    assertThat(actual.result()).isEqualTo(MODULE_BOOTSTRAP);
  }

  @Test
  void getEgressBootstrap_positive() {
    var appIds = java.util.List.of("app-foo-1.0.0");
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(AUTH_TOKEN));
    when(appManagerClient.getEgressBootstrap(MODULE_ID, appIds, AUTH_TOKEN))
      .thenReturn(succeededFuture(MODULE_BOOTSTRAP));

    var actual = service.getEgressBootstrap(appIds);

    assertThat(actual.result()).isEqualTo(MODULE_BOOTSTRAP);
  }

  @Test
  void getEgressBootstrap_negative_propagatesClientFailure() {
    var appIds = java.util.List.of("app-foo-1.0.0");
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(AUTH_TOKEN));
    when(appManagerClient.getEgressBootstrap(MODULE_ID, appIds, AUTH_TOKEN))
      .thenReturn(Future.failedFuture(new RuntimeException("am down")));

    var actual = service.getEgressBootstrap(appIds);

    assertThat(actual.failed()).isTrue();
    assertThat(actual.cause()).hasMessage("am down");
  }

  @Test
  void getIngressBootstrap_negative_propagatesClientFailure() {
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(AUTH_TOKEN));
    when(appManagerClient.getIngressBootstrap(MODULE_ID, AUTH_TOKEN))
      .thenReturn(Future.failedFuture(new RuntimeException("am down")));

    var actual = service.getIngressBootstrap();

    assertThat(actual.failed()).isTrue();
    assertThat(actual.cause()).hasMessage("am down");
  }
}
