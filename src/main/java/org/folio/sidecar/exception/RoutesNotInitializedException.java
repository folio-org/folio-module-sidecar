package org.folio.sidecar.exception;

/**
 * Thrown while module routes are not loaded from mgr-applications yet.
 *
 * <p>The condition is transient; callers should retry.</p>
 */
public class RoutesNotInitializedException extends RuntimeException {

  public RoutesNotInitializedException() {
    super("Module routes are not initialized yet, please retry");
  }
}
