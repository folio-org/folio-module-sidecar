package org.folio.sidecar.integration.am;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.MODULE_PROPERTIES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.folio.sidecar.integration.am.model.EgressBootstrapResult;
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
  void getModuleBootstrapIngress_positive() {
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(AUTH_TOKEN));
    when(appManagerClient.getModuleBootstrapIngress(MODULE_ID, AUTH_TOKEN))
      .thenReturn(succeededFuture(MODULE_BOOTSTRAP));

    var actual = service.getModuleBootstrapIngress();

    assertThat(actual.result()).isEqualTo(MODULE_BOOTSTRAP);
  }

  @Test
  void getModuleBootstrapEgress_delegatesModuleIdScopeAndToken() {
    var apps = List.of("app-a-1.0.0", "app-b-1.0.0");
    var egress = Optional.of(new EgressBootstrapResult());
    when(tokenProvider.getAdminToken()).thenReturn(succeededFuture(AUTH_TOKEN));
    when(appManagerClient.getModuleBootstrapEgress(MODULE_ID, apps, AUTH_TOKEN))
      .thenReturn(succeededFuture(egress));

    var actual = service.getModuleBootstrapEgress(apps);

    assertThat(actual.result()).isSameAs(egress);
    // module id comes from module properties, applicationIds are forwarded verbatim, admin token is threaded through
    verify(appManagerClient).getModuleBootstrapEgress(MODULE_ID, apps, AUTH_TOKEN);
  }
}
