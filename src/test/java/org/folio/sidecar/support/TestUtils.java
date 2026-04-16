package org.folio.sidecar.support;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tools.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION;
import static tools.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

  public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
    .changeDefaultPropertyInclusion(include ->
      include.withValueInclusion(JsonInclude.Include.NON_NULL)
        .withContentInclusion(JsonInclude.Include.NON_NULL))
    .configure(INCLUDE_SOURCE_IN_LOCATION, true)
    .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    .configure(SORT_PROPERTIES_ALPHABETICALLY, false)
    .build();

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
