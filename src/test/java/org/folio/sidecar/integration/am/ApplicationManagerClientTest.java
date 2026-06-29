package org.folio.sidecar.integration.am;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestConstants.AM_PROPERTIES;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP_EGRESS;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestUtils.OBJECT_MAPPER;
import static org.folio.sidecar.support.TestUtils.readString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.HttpStatus;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.service.JsonConverter;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationManagerClientTest {

  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @Mock private WebClient webClient;
  @Mock private HttpRequest<Buffer> request;
  @Mock private HttpResponse<Buffer> response;

  @Captor private ArgumentCaptor<String> uriCaptor;
  @Captor private ArgumentCaptor<io.vertx.core.json.JsonObject> bodyCaptor;

  private ApplicationManagerClient appManagerClient;

  @BeforeEach
  void setUp() {
    appManagerClient = new ApplicationManagerClient(webClient, jsonConverter, AM_PROPERTIES);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(webClient, response, response);
  }

  @Test
  void getModuleBootstrap_positive() {
    when(webClient.getAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.send()).thenReturn(Future.succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(readString("json/module-bootstrap.json"));

    var actual = appManagerClient.getModuleBootstrap(MODULE_ID, AUTH_TOKEN);

    assertThat(actual.result()).isEqualTo(MODULE_BOOTSTRAP);

    verify(request).putHeader(CONTENT_TYPE, APPLICATION_JSON);
    verify(request).putHeader(eq(TOKEN), anyString());
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1");
    verify(jsonConverter).parseResponse(response, ModuleBootstrap.class);
  }

  @Test
  void getIngressBootstrap_positive() {
    when(webClient.getAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.send()).thenReturn(Future.succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(readString("json/module-bootstrap.json"));

    var actual = appManagerClient.getIngressBootstrap(MODULE_ID, AUTH_TOKEN);

    assertThat(actual.result()).isEqualTo(MODULE_BOOTSTRAP);
    verify(request).putHeader(CONTENT_TYPE, APPLICATION_JSON);
    verify(request).putHeader(eq(TOKEN), anyString());
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1/bootstrap");
    verify(jsonConverter).parseResponse(response, ModuleBootstrap.class);
  }

  @Test
  void getEgressBootstrap_positive() {
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.sendJsonObject(bodyCaptor.capture())).thenReturn(Future.succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(readString("json/module-bootstrap-egress.json"));

    var actual = appManagerClient.getEgressBootstrap(MODULE_ID, java.util.List.of("app-foo-1.0.0"), AUTH_TOKEN);

    assertThat(actual.result().getRequiredModules())
      .isEqualTo(MODULE_BOOTSTRAP_EGRESS.getRequiredModules());
    verify(request).putHeader(CONTENT_TYPE, APPLICATION_JSON);
    verify(request).putHeader(eq(TOKEN), anyString());
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1/bootstrap");
    assertThat(bodyCaptor.getValue().getJsonArray("applicationIds"))
      .isEqualTo(new io.vertx.core.json.JsonArray(java.util.List.of("app-foo-1.0.0")));
    verify(jsonConverter).parseResponse(response, ModuleBootstrap.class);
  }

  @Test
  void getEgressBootstrap_positive_multipleApplicationIds() {
    var appIds = java.util.List.of("app-foo-1.0.0", "app-bar-2.0.0");
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.sendJsonObject(bodyCaptor.capture())).thenReturn(Future.succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(readString("json/module-bootstrap-egress.json"));

    var actual = appManagerClient.getEgressBootstrap(MODULE_ID, appIds, AUTH_TOKEN);

    assertThat(actual.result().getRequiredModules())
      .isEqualTo(MODULE_BOOTSTRAP_EGRESS.getRequiredModules());
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1/bootstrap");
    assertThat(bodyCaptor.getValue().getJsonArray("applicationIds"))
      .isEqualTo(new io.vertx.core.json.JsonArray(java.util.List.of("app-foo-1.0.0", "app-bar-2.0.0")));
    verify(jsonConverter).parseResponse(response, ModuleBootstrap.class);
  }

  @Test
  void getEgressBootstrap_negative_propagatesFailure() {
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.sendJsonObject(any())).thenReturn(Future.failedFuture(new RuntimeException("am down")));

    var actual = appManagerClient.getEgressBootstrap(MODULE_ID, java.util.List.of("app-foo-1.0.0"), AUTH_TOKEN);

    assertThat(actual.failed()).isTrue();
    assertThat(actual.cause()).hasMessage("am down");
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1/bootstrap");
  }

  @Test
  void getIngressBootstrap_negative_propagatesFailure() {
    when(webClient.getAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.send()).thenReturn(Future.failedFuture(new RuntimeException("am down")));

    var actual = appManagerClient.getIngressBootstrap(MODULE_ID, AUTH_TOKEN);

    assertThat(actual.failed()).isTrue();
    assertThat(actual.cause()).hasMessage("am down");
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1/bootstrap");
  }
}
