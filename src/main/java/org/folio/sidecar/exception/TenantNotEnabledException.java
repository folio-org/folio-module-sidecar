package org.folio.sidecar.exception;

public class TenantNotEnabledException extends RuntimeException {

  public TenantNotEnabledException(String tenantName) {
    super("Application is not enabled for tenant: " + tenantName);
  }
}
