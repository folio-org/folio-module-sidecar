package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SemverUtilsTest {

  @ParameterizedTest
  @MethodSource("hasVersionDataProvider")
  void hasVersion_positive(String moduleId, boolean expected) {
    assertThat(SemverUtils.hasVersion(moduleId)).isEqualTo(expected);
  }

  public static Stream<Arguments> hasVersionDataProvider() {
    return Stream.of(
      arguments("mod-foo-1.2.3", true),
      arguments("mod-foo-1.2.3-alpha", true),
      arguments("mod-foo-1.2.3-alpha.1", true),
      arguments("mod-foo-1.2.3+build.1", true),
      arguments("mod-foo-1.2.3-alpha.1+build.1", true),
      arguments("mod-foo-1.2", false),
      arguments("mod-foo1.2", false),
      arguments("mod-foo", false)
    );
  }
}
