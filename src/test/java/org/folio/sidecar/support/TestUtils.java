package org.folio.sidecar.support;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;
import java.io.File;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.folio.sidecar.service.SidecarSignatureService;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

  @SneakyThrows
  public static String readString(String path) {
    var resource = TestUtils.class.getClassLoader().getResource(path);
    var file = new File(Objects.requireNonNull(resource).toURI());
    return FileUtils.readFileToString(file, UTF_8.name());
  }

  @SneakyThrows
  public static File classpathFile(String path) {
    var resource = TestUtils.class.getClassLoader().getResource(path);
    return new File(Objects.requireNonNull(resource).toURI());
  }

  @SneakyThrows
  public static <T> T parse(String result, Class<T> type) {
    return OBJECT_MAPPER.readValue(result, type);
  }

  @SneakyThrows
  public static <T> T parse(String result, TypeReference<T> type) {
    return OBJECT_MAPPER.readValue(result, type);
  }

  @SneakyThrows
  public static String minify(String json) {
    return OBJECT_MAPPER.readTree(json).toString();
  }

  public static String asJson(String path) {
    return minify(readString(path));
  }

  public static RequestSpecification givenJson() {
    return given()
      .log().ifValidationFails(LogDetail.BODY)
      .contentType(APPLICATION_JSON);
  }

  @SneakyThrows
  public static String getSignature() {
    var clazz = SidecarSignatureService.class;
    var nameField = clazz.getDeclaredField("SIGNATURE");
    nameField.setAccessible(true);
    return (String) nameField.get(null);
  }
}
