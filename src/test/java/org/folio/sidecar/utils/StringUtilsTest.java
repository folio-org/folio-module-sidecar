package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.support.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class StringUtilsTest {

  @ParameterizedTest
  @CsvSource({"test,false", " ,true", "'',true", "'  ',false"})
  void isEmpty_parameterized(String given, boolean expected) {
    var actual = StringUtils.isEmpty(given);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"test,false", " ,true", "'',true", "'  ',true"})
  void isBlank_parameterized(String given, boolean expected) {
    var actual = StringUtils.isBlank(given);
    assertThat(actual).isEqualTo(expected);
  }
}
