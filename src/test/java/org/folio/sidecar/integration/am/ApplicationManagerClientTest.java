package org.folio.sidecar.integration.am;

import static io.vertx.core.Future.succeededFuture;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestConstants.AM_PROPERTIES;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP;
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
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.folio.sidecar.integration.am.model.EgressBootstrapResult;
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
  void getModuleBootstrapEgress_positive_returnsTenantMap() {
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.sendJson(any())).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(
      "{\"egress\":{\"tenant1\":{\"found\":true,\"bootstrap\":{\"module\":{\"moduleId\":\"mod-foo-0.2.1\","
        + "\"applicationId\":\"application-0.0.1\"},\"requiredModules\":[]}}}}");

    var actual = appManagerClient.getModuleBootstrapEgress(
      MODULE_ID, Map.of("tenant1", List.of("application-0.0.1")), AUTH_TOKEN);

    assertThat(actual.succeeded()).isTrue();
    var opt = actual.result();
    assertThat(opt).isPresent();
    assertThat(opt.get()).containsKey("tenant1");
    EgressBootstrapResult result = opt.get().get("tenant1");
    assertThat(result.isFound()).isTrue();

    verify(request).putHeader(CONTENT_TYPE, APPLICATION_JSON);
    verify(request).putHeader(eq(TOKEN), anyString());
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1/bootstrap");
  }

  @Test
  void getModuleBootstrapEgress_endpointMissing_returnsEmptyOptional() {
    when(webClient.postAbs(uriCaptor.capture())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.sendJson(any())).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);

    var actual = appManagerClient.getModuleBootstrapEgress(
      MODULE_ID, Map.of("tenant1", List.of("application-0.0.1")), AUTH_TOKEN);

    assertThat(actual.succeeded()).isTrue();
    assertThat(actual.result()).isEmpty();

    verify(request).putHeader(CONTENT_TYPE, APPLICATION_JSON);
    verify(request).putHeader(eq(TOKEN), anyString());
    assertThat(uriCaptor.getValue()).isEqualTo("http://am:8081/modules/mod-foo-0.2.1/bootstrap");
  }
}
