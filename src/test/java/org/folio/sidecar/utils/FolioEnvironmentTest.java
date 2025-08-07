package org.folio.sidecar.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class FolioEnvironmentTest {

  static Stream<Arguments> getSecureStoreEnvName() {
    return Stream.of(
        Arguments.of(Map.of("SECURE_STORE_ENV", "a", "ENV", "c"), Map.of("secure.store.env", "b", "env", "d"), "a"),
        Arguments.of(Map.of("SECURE_STORE_ENV", "", "ENV", "c"), Map.of("secure.store.env", "b", "env", "d"), "b"),
        Arguments.of(Map.of("ENV", "c"), Map.of("env", "d"), "c"),
        Arguments.of(Map.of(), Map.of("env", "d"), "d"),
        Arguments.of(Map.of(), Map.of(), "folio"),
        Arguments.of(Map.of("x", "a", "y", "c"), Map.of("z", "b", "1", "d"), "folio"));
  }

  @ParameterizedTest
  @MethodSource
  void getSecureStoreEnvName(Map<String, String> env, Map<String, String> propertiesMap, String expected) {
    var properties = new Properties();
    properties.putAll(propertiesMap);
    assertThat(FolioEnvironment.getSecureStoreEnvName(env, properties), is(expected));
  }
}
