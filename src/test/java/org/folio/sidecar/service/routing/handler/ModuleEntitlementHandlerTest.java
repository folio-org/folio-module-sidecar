package org.folio.sidecar.service.routing.handler;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.security.ForbiddenException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.folio.sidecar.service.TenantService;
import org.folio.sidecar.support.TestConstants;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleEntitlementHandlerTest {

  @Mock private TenantService tenantService;
  private ModuleEntitlementHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ModuleEntitlementHandler(TestConstants.MODULE_ID, tenantService);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(tenantService);
  }

  @Test
  void handle_positive() {
    var rc = mockGetRequest("/entitlements/modules/" + TestConstants.MODULE_ID);
    var response = mockResponseChain(rc);
    when(tenantService.getEnabledTenants()).thenReturn(succeededFuture(Set.of("tenant1", "tenant2")));

    var result = handler.handle(rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isTrue();

    var bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).end(bodyCaptor.capture());
    var body = new JsonArray(bodyCaptor.getValue());
    assertThat(body.contains("tenant1")).isTrue();
    assertThat(body.contains("tenant2")).isTrue();
    assertThat(body.size()).isEqualTo(2);
  }

  @Test
  void handle_positive_noTenants() {
    var rc = mockGetRequest("/entitlements/modules/" + TestConstants.MODULE_ID);
    var response = mockResponseChain(rc);
    when(tenantService.getEnabledTenants()).thenReturn(succeededFuture(Set.of()));

    var result = handler.handle(rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isTrue();

    var bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).end(bodyCaptor.capture());
    assertThat(bodyCaptor.getValue()).isEqualTo("[]");
  }

  @Test
  void handle_negative_wrongModuleId() {
    var rc = mockGetRequest("/entitlements/modules/mod-bar-1.0.0");

    var result = handler.handle(rc);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void handle_positive_wrongMethod() {
    var rc = mockRequest("/entitlements/modules/" + TestConstants.MODULE_ID, HttpMethod.POST);

    var result = handler.handle(rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isFalse();
  }

  @Test
  void handle_positive_wrongPath() {
    var rc = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(rc.request()).thenReturn(request);
    when(request.path()).thenReturn("/foo/entities");

    var result = handler.handle(rc);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isFalse();
  }

  @Test
  void handle_negative_loadingFailed() {
    var rc = mockGetRequest("/entitlements/modules/" + TestConstants.MODULE_ID);
    var error = new RuntimeException("load failed");
    when(tenantService.getEnabledTenants()).thenReturn(failedFuture(error));

    var result = handler.handle(rc);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isEqualTo(error);
  }

  private RoutingContext mockGetRequest(String path) {
    return mockRequest(path, HttpMethod.GET);
  }

  private RoutingContext mockRequest(String path, HttpMethod method) {
    var rc = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    when(rc.request()).thenReturn(request);
    when(request.path()).thenReturn(path);
    when(request.method()).thenReturn(method);
    return rc;
  }

  private HttpServerResponse mockResponseChain(RoutingContext rc) {
    var response = mock(HttpServerResponse.class);
    when(rc.response()).thenReturn(response);
    when(response.setStatusCode(HttpStatus.SC_OK)).thenReturn(response);
    when(response.putHeader(CONTENT_TYPE, APPLICATION_JSON)).thenReturn(response);
    return response;
  }
}
