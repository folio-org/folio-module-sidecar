package org.folio.sidecar.service.header;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.vertx.core.MultiMap;
import org.folio.sidecar.exception.DuplicateHeaderException;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OkapiHeaderValidatorTest {

  private OkapiHeaderValidator validator;

  @BeforeEach
  void setUp() {
    validator = new OkapiHeaderValidator();
  }

  @Test
  void validateOkapiHeaders_positive() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add(OkapiHeaders.TENANT, "diku");
    headers.add(OkapiHeaders.TOKEN, "token-value");

    assertDoesNotThrow(() -> validator.validateOkapiHeaders(headers));
  }

  @Test
  void validateOkapiHeaders_withDuplicateTenant_negative() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add(OkapiHeaders.TENANT, "diku");
    headers.add(OkapiHeaders.TENANT, "other");

    assertThrows(DuplicateHeaderException.class, () -> validator.validateOkapiHeaders(headers));
  }

  @Test
  void validateOkapiHeaders_withDuplicateToken_negative() {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add(OkapiHeaders.TOKEN, "token1");
    headers.add(OkapiHeaders.TOKEN, "token2");

    assertThrows(DuplicateHeaderException.class, () -> validator.validateOkapiHeaders(headers));
  }
}