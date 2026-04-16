package org.folio.sidecar.configuration;

import static tools.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION;
import static tools.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Log4j2
@Dependent
@RequiredArgsConstructor
public class ObjectMapperConfiguration {

  @Produces
  @ApplicationScoped
  @Named("objectMapper")
  public ObjectMapper objectMapper() {
    return JsonMapper.builder()
      .changeDefaultPropertyInclusion(include ->
        include.withValueInclusion(JsonInclude.Include.NON_NULL)
          .withContentInclusion(JsonInclude.Include.NON_NULL))
      .configure(INCLUDE_SOURCE_IN_LOCATION, true)
      .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
      .configure(SORT_PROPERTIES_ALPHABETICALLY, false)
      .build();
  }
}
