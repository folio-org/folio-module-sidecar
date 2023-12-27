package org.folio.sidecar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class JsonConverter {

  private static final String FAILED_TO_PARSE_HTTP_RESPONSE_MSG = "Failed to parse http response: ";

  private final ObjectMapper objectMapper;

  /**
   * Converts passed {@link Object} value to json string.
   *
   * @param value value to convert
   * @return json value as {@link String}.
   */
  public String toJson(Object value) {
    if (value == null) {
      return null;
    }

    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new BadRequestException("Failed to write value as json: " + error.getMessage());
    }
  }

  /**
   * Converts given json {@link String} object as java object.
   *
   * @param content - json {@link String} content
   * @param type - return type as {@link TypeReference} object
   * @param <T> - generic type for return object
   * @return parsed {@link T} object from buffered http response
   */
  public <T> T fromJson(String content, TypeReference<T> type) {
    if (content == null) {
      return null;
    }

    try {
      return objectMapper.readValue(content, type);
    } catch (JsonProcessingException error) {
      throw new BadRequestException(FAILED_TO_PARSE_HTTP_RESPONSE_MSG + error.getMessage());
    }
  }

  /**
   * Parses {@link HttpResponse} from {@link io.vertx.ext.web.client.WebClient} component.
   *
   * @param response - {@link HttpResponse} with {@link Buffer} as response content
   * @param type - return type class
   * @param <T> - generic type for return object
   * @return parsed {@link T} object from buffered http response
   */
  public <T> T parseResponse(HttpResponse<Buffer> response, Class<T> type) {
    try {
      return objectMapper.readValue(getResponseBody(response), type);
    } catch (JsonProcessingException error) {
      throw new BadRequestException(FAILED_TO_PARSE_HTTP_RESPONSE_MSG + error.getMessage());
    }
  }

  /**
   * Parses {@link HttpResponse} from {@link io.vertx.ext.web.client.WebClient} component.
   *
   * @param response - {@link HttpResponse} with {@link Buffer} as response content
   * @param type - return type as {@link TypeReference} object
   * @param <T> - generic type for return object
   * @return parsed {@link T} object from buffered http response
   */
  public <T> T parseResponse(HttpResponse<Buffer> response, TypeReference<T> type) {
    try {
      return objectMapper.readValue(getResponseBody(response), type);
    } catch (JsonProcessingException error) {
      throw new BadRequestException(FAILED_TO_PARSE_HTTP_RESPONSE_MSG + error.getMessage());
    }
  }

  private static String getResponseBody(HttpResponse<Buffer> response) {
    var responseBody = response.bodyAsString();
    var status = response.statusCode();
    if (status >= 400) {
      throw new WebApplicationException("Failed to perform request: " + responseBody, status);
    }
    return responseBody;
  }
}
