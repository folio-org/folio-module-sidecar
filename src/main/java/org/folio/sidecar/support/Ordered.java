package org.folio.sidecar.support;

@FunctionalInterface
public interface Ordered {

  /**
   * Useful constant for the highest precedence value.
   */
  @SuppressWarnings("unused")
  int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

  /**
   * Useful constant for the lowest precedence value.
   */
  @SuppressWarnings("unused")
  int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

  /**
   * Get the order value of this object.
   *
   * <p>Higher values are interpreted as lower priority. As a consequence, the object
   * with the lowest value has the highest priority.</p>
   *
   * @return the order value
   * @see #HIGHEST_PRECEDENCE
   * @see #LOWEST_PRECEDENCE
   */
  int getOrder();
}
