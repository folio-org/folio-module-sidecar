package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class SemverUtilsTest {

  @ParameterizedTest
  @MethodSource("hasVersionDataProvider")
  void hasVersion_positive(String moduleId, boolean expected) {
    assertThat(SemverUtils.hasVersion(moduleId)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("getNamePositiveDataProvider")
  void getName_positive_returnsNamePart(String sourceId, String expectedName) {
    var actualName = SemverUtils.getName(sourceId);

    assertThat(actualName).isEqualTo(expectedName);
  }

  @ParameterizedTest
  @MethodSource("getNameNegativeDataProvider")
  void getName_negative_throwsIllegalArgumentException(String invalidSourceId) {
    assertThatThrownBy(() -> SemverUtils.getName(invalidSourceId))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Source does not contain a valid semantic version");
  }

  private static Stream<Arguments> hasVersionDataProvider() {
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

  private static Stream<Arguments> getNamePositiveDataProvider() {
    return Stream.of(
      arguments("mod-foo-1.2.3", "mod-foo"),
      arguments("mod-bar-2.0.0", "mod-bar"),
      arguments("my-module-10.5.23", "my-module"),
      arguments("mod-foo-1.2.3-alpha", "mod-foo"),
      arguments("mod-foo-1.2.3-alpha.1", "mod-foo"),
      arguments("mod-foo-1.2.3-beta.2", "mod-foo"),
      arguments("mod-foo-1.2.3+build.1", "mod-foo"),
      arguments("mod-foo-1.2.3-alpha.1+build.1", "mod-foo"),
      arguments("module-with-dashes-0.0.1", "module-with-dashes"),
      arguments("a-1.0.0", "a")
    );
  }

  private static Stream<String> getNameNegativeDataProvider() {
    return Stream.of(
      "mod-foo-1.2",
      "mod-foo1.2.3",
      "mod-foo",
      "1.2.3",
      "-1.2.3",
      "mod-foo-",
      "mod-foo-1",
      "mod-foo-1.2.3.4",
      ""
    );
  }
}
