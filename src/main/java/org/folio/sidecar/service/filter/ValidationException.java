package org.folio.sidecar.service.filter;

import lombok.Getter;
import org.folio.sidecar.model.error.Error;

@Getter
public class ValidationException extends RuntimeException {

  private final Error error;

  public ValidationException(Error error) {
    super(error.getMessage());
    this.error = error;
  }
}