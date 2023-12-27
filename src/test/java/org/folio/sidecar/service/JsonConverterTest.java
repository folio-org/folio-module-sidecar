package org.folio.sidecar.service;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.sidecar.support.TestUtils;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JsonConverterTest {

  private static final String JSON_BODY = "{\"field\":\"value\"}";
  private static final String INVALID_JSON_BODY = "{\"field\":value}";
  private static final String FIELD_VALUE = "value";

  @InjectMocks private JsonConverter jsonConverter;
  @Mock private HttpResponse<Buffer> httpResponse;
  @Spy private final ObjectMapper objectMapper = TestUtils.OBJECT_MAPPER;

  @Test
  void toJson_positive() throws JsonProcessingException {
    var actual = jsonConverter.toJson(TestClass.of(FIELD_VALUE));
    assertThat(actual).isEqualTo(JSON_BODY);

    verify(objectMapper).writeValueAsString(TestClass.of(FIELD_VALUE));
  }

  @Test
  void toJson_positive_nullValue() {
    var actual = jsonConverter.toJson(null);
    assertThat(actual).isNull();
  }

  @Test
  void toJson_negative_throwsException() {
    var value = new NonSerializableByJacksonClass();
    assertThatThrownBy(() -> jsonConverter.toJson(value))
      .isInstanceOf(BadRequestException.class)
      .hasMessageContaining("Failed to write value as json: Direct self-reference leading to cycle");
  }

  @Test
  void fromJson_positive_typeReference() throws Exception {
    var actual = jsonConverter.fromJson(JSON_BODY, new TypeReference<TestClass>() {});
    assertThat(actual).isEqualTo(TestClass.of(FIELD_VALUE));
    verify(objectMapper).readValue(eq(JSON_BODY), ArgumentMatchers.<TypeReference<TestClass>>any());
  }

  @Test
  void fromJsonForClass_positive_nullForTypeReference() {
    var actual = jsonConverter.fromJson(null, new TypeReference<TestClass>() {});
    assertThat(actual).isNull();
    verifyNoInteractions(objectMapper);
  }

  @Test
  void fromJson_negative_typeReference() throws Exception {
    var typeReference = new TypeReference<TestClass>() {};
    assertThatThrownBy(() -> jsonConverter.fromJson(INVALID_JSON_BODY, typeReference))
      .isInstanceOf(BadRequestException.class)
      .hasMessageContaining("Failed to parse http response: Unrecognized token 'value'");
    verify(objectMapper).readValue(eq(INVALID_JSON_BODY), ArgumentMatchers.<TypeReference<TestClass>>any());
  }

  @Test
  void parseResponseBody_positive_class() throws Exception {
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpResponse.bodyAsString()).thenReturn(JSON_BODY);

    var actual = jsonConverter.parseResponse(httpResponse, TestClass.class);

    assertThat(actual).isEqualTo(TestClass.of(FIELD_VALUE));
    verify(objectMapper).readValue(JSON_BODY, TestClass.class);
  }

  @Test
  void parseResponseBody_negative_classWithInvalidJson() throws Exception {
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpResponse.bodyAsString()).thenReturn(INVALID_JSON_BODY);

    assertThatThrownBy(() -> jsonConverter.parseResponse(httpResponse, TestClass.class))
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Failed to parse http response: Unrecognized token 'value': "
        + "was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n"
        + " at [Source: (String)\"{\"field\":value}\"; line: 1, column: 15]");
    verify(objectMapper).readValue(INVALID_JSON_BODY, TestClass.class);
  }

  @Test
  void parseResponseBody_negative_classForInvalidStatusCode() {
    when(httpResponse.statusCode()).thenReturn(SC_BAD_REQUEST);
    when(httpResponse.bodyAsString()).thenReturn("Bad Request");

    assertThatThrownBy(() -> jsonConverter.parseResponse(httpResponse, TestClass.class))
      .isInstanceOf(WebApplicationException.class)
      .hasMessage("Failed to perform request: Bad Request")
      .satisfies(error ->
        assertThat(((WebApplicationException) error).getResponse()).satisfies(resp ->
          assertThat(resp.getStatus()).isEqualTo(SC_BAD_REQUEST)));
    verifyNoInteractions(objectMapper);
  }

  @Test
  void parseResponseBody_positive_typeReference() throws Exception {
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpResponse.bodyAsString()).thenReturn(JSON_BODY);

    var actual = jsonConverter.parseResponse(httpResponse, new TypeReference<TestClass>() {});

    assertThat(actual).isEqualTo(TestClass.of(FIELD_VALUE));
    verify(objectMapper).readValue(eq(JSON_BODY), ArgumentMatchers.<TypeReference<TestClass>>any());
  }

  @Test
  void parseResponseBody_negative_typeReferenceWithInvalidJson() throws Exception {
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpResponse.bodyAsString()).thenReturn(INVALID_JSON_BODY);
    var typeRef = new TypeReference<TestClass>() {};

    assertThatThrownBy(() -> jsonConverter.parseResponse(httpResponse, typeRef))
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Failed to parse http response: Unrecognized token 'value': "
        + "was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n"
        + " at [Source: (String)\"{\"field\":value}\"; line: 1, column: 15]");
    verify(objectMapper).readValue(eq(INVALID_JSON_BODY), ArgumentMatchers.<TypeReference<TestClass>>any());
  }

  @Test
  void parseResponseBody_negative_typeReferenceForInvalidStatusCode() {
    when(httpResponse.statusCode()).thenReturn(SC_BAD_REQUEST);
    when(httpResponse.bodyAsString()).thenReturn("Bad Request");
    var typeRef = new TypeReference<TestClass>() {};

    assertThatThrownBy(() -> jsonConverter.parseResponse(httpResponse, typeRef))
      .isInstanceOf(WebApplicationException.class)
      .hasMessage("Failed to perform request: Bad Request")
      .satisfies(error ->
        assertThat(((WebApplicationException) error).getResponse()).satisfies(resp ->
          assertThat(resp.getStatus()).isEqualTo(SC_BAD_REQUEST)));
    verifyNoInteractions(objectMapper);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  private static final class TestClass {

    private String field;
  }

  private static final class NonSerializableByJacksonClass {

    private final NonSerializableByJacksonClass self = this;

    @SuppressWarnings("unused")
    public NonSerializableByJacksonClass getSelf() {
      return self;
    }
  }
}
