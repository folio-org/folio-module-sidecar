package org.folio.sidecar.utils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.sidecar.utils.JwtUtils.trimTokenBearer;

import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class JwtUtilsTest {

  @Test
  void trimTokenBearer_positive() {
    var token = "Bearer token";
    var result = trimTokenBearer(token);

    assertThat(result).isEqualTo("token");
  }

  @Test
  void trimTokenBearer_positive_nullToken() {
    var result = trimTokenBearer(null);

    assertThat(result).isNull();
  }

  @Test
  void trimTokenBearer_positive_bearerTokenNotExists() {
    var result = trimTokenBearer("org/folio/sidecar/service/token");

    assertThat(result).isEqualTo("org/folio/sidecar/service/token");
  }
}
