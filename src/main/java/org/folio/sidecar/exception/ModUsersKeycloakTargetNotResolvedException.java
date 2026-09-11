package org.folio.sidecar.exception;

/**
 * Raised when the {@code mod-users-keycloak} version or address of a tenant cannot be resolved.
 *
 * <p>The sidecar answers 503 with Retry-After instead of guessing an address: calling the wrong version silently is
 * worse than failing visibly, and there is no static fallback. Resolution is attempted once per request, so a later
 * request simply tries again.</p>
 */
public class ModUsersKeycloakTargetNotResolvedException extends RuntimeException {

  public ModUsersKeycloakTargetNotResolvedException(String tenant) {
    super("mod-users-keycloak target is not resolved for tenant: " + tenant);
  }

  public ModUsersKeycloakTargetNotResolvedException(String tenant, Throwable cause) {
    super("mod-users-keycloak target is not resolved for tenant: " + tenant, cause);
  }
}
