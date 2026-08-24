package org.folio.sidecar.exception;

/**
 * Thrown when tenant entitlements are unavailable because loading failed.
 *
 * <p>The condition is transient; callers should retry.</p>
 */
public class EntitlementsNotLoadedException extends RuntimeException {

  /**
   * Constructs a new exception wrapping the original failure that prevented entitlements from loading.
   *
   * @param cause the original error raised while loading tenant entitlements
   */
  public EntitlementsNotLoadedException(Throwable cause) {
    super("Tenant entitlements are not loaded yet, please retry", cause);
  }
}
