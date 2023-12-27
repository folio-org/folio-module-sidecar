package org.folio.sidecar.integration.tm;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.TENANT_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_NAME;
import static org.folio.sidecar.support.TestConstants.TENANT_UUID;
import static org.folio.sidecar.support.TestConstants.TM_PROPERTIES;
import static org.folio.sidecar.support.TestUtils.OBJECT_MAPPER;
import static org.folio.sidecar.support.TestUtils.readString;
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
import org.apache.http.HttpStatus;
import org.folio.sidecar.integration.tm.model.Tenant;
import org.folio.sidecar.service.JsonConverter;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantManagerClientTest {

  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @Mock private WebClient webClient;
  @Mock private HttpRequest<Buffer> request;
  @Mock private HttpResponse<Buffer> response;

  @Captor private ArgumentCaptor<String> uriCaptor;

  private TenantManagerClient client;

  @BeforeEach
  void setUp() {
    client = new TenantManagerClient(webClient, jsonConverter, TM_PROPERTIES);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(webClient, response, response);
  }

  @Test
  void getTenant() {
    when(webClient.getAbs(uriCaptor.capture())).thenReturn(request);
    when(request.addQueryParam(anyString(), anyString())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.send()).thenReturn(Future.succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(readString("json/tenants.json"));

    var actual = client.getTenantInfo(List.of(TENANT_ID), AUTH_TOKEN);

    assertThat(actual.result()).isEqualTo(List.of(
      Tenant.of(TENANT_UUID, TENANT_NAME, "test description")));

    verify(request).addQueryParam("query", format("id == (\"%s\")", TENANT_ID));
    verify(request).putHeader(CONTENT_TYPE, APPLICATION_JSON);
    verify(request).putHeader(eq(TOKEN), anyString());
    verify(request).addQueryParam("limit", Integer.toString(TM_PROPERTIES.batchSize));
    assertThat(uriCaptor.getValue()).isEqualTo("http://tm:8081/tenants");
  }
}
