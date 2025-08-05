package org.folio.sidecar.integration.te;

import static io.vertx.core.Future.succeededFuture;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.support.TestConstants.APPLICATION_ID;
import static org.folio.sidecar.support.TestConstants.AUTH_TOKEN;
import static org.folio.sidecar.support.TestConstants.MODULE_ID;
import static org.folio.sidecar.support.TestConstants.TENANT_ID;
import static org.folio.sidecar.support.TestConstants.TE_PROPERTIES;
import static org.folio.sidecar.support.TestUtils.OBJECT_MAPPER;
import static org.folio.sidecar.support.TestUtils.readString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.HttpStatus;
import org.folio.sidecar.integration.te.model.Entitlement;
import org.folio.sidecar.model.ResultList;
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
class TenantEntitlementClientTest {

  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @Mock private WebClient webClient;
  @Mock private HttpRequest<Buffer> request;
  @Mock private HttpResponse<Buffer> response;

  @Captor private ArgumentCaptor<String> uriCaptor;

  private TenantEntitlementClient client;

  @BeforeEach
  void setUp() {
    client = new TenantEntitlementClient(webClient, jsonConverter, TE_PROPERTIES);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(webClient, response, response);
  }

  @Test
  void getTenantEntitlements() {
    when(webClient.getAbs(uriCaptor.capture())).thenReturn(request);
    when(request.addQueryParam(anyString(), anyString())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(readString("json/tenant-entitlements.json"));

    var actual = client.getModuleEntitlements(MODULE_ID, AUTH_TOKEN);

    assertThat(actual.result()).isEqualTo(ResultList.asSinglePage(
      Entitlement.of(APPLICATION_ID, TENANT_ID, null)));

    verify(request).addQueryParam("limit", Integer.toString(TE_PROPERTIES.getBatchSize()));
    verify(request).addQueryParam("offset", "0");
    verify(request).putHeader(CONTENT_TYPE, APPLICATION_JSON);
    verify(request).putHeader(eq(TOKEN), anyString());
    assertThat(uriCaptor.getValue()).isEqualTo("http://te:8081/entitlements/modules/mod-foo-0.2.1");
  }

  @Test
  void getTenantEntitlements_negative_notValidBatchSize() {
    TenantEntitlementClientProperties properties =
      new TenantEntitlementClientProperties("http://te:8081", 0);

    assertThrows(IllegalArgumentException.class,
      () -> new TenantEntitlementClient(webClient, jsonConverter, properties),
      "Batch size should not be less than 1");
  }

  @Test
  void getTenantEntitlements_negative_negativeValidBatchSizeValue() {
    TenantEntitlementClientProperties properties =
      new TenantEntitlementClientProperties("http://te:8081", -5);

    assertThrows(IllegalArgumentException.class,
      () -> new TenantEntitlementClient(webClient, jsonConverter, properties),
      "Batch size should not be less than 1");
  }

  @Test
  void getTenantEntitlements_positive_limitQueryParamIsAdded() {
    var mockClientProperties = org.mockito.Mockito.mock(TenantEntitlementClientProperties.class);
    org.mockito.Mockito.when(mockClientProperties.getBatchSize()).thenReturn(500);

    // Setup mocks for fluent API
    when(webClient.getAbs(anyString())).thenReturn(request);
    when(request.addQueryParam(anyString(), anyString())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.send()).thenReturn(succeededFuture(response));
    when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.bodyAsString()).thenReturn(readString("json/tenant-entitlements.json"));

    // ArgumentCaptors for key and value
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

    var teClient = new TenantEntitlementClient(webClient, jsonConverter, mockClientProperties);
    teClient.getTenantEntitlements("test-tenant", true, "test-token");

    // Verify addQueryParam called three times
    verify(request, org.mockito.Mockito.times(3)).addQueryParam(keyCaptor.capture(), valueCaptor.capture());

    // Assert that one of the calls was for "limit" with value "500"
    assertThat(keyCaptor.getAllValues()).contains("limit");
    int limitIndex = keyCaptor.getAllValues().indexOf("limit");
    assertThat(valueCaptor.getAllValues().get(limitIndex)).isEqualTo("500");
  }
}
