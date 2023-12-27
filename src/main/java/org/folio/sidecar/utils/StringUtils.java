package org.folio.sidecar.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StringUtils {

  /**
   * Checks if given string is empty or not.
   *
   * @param str - {@link String} object to check
   * @return true if given string is empty, false - otherwise
   */
  public static boolean isEmpty(String str) {
    return str == null || str.isEmpty();
  }

  /**
   * Checks if given string is blank or not.
   *
   * @param str - {@link String} object to check
   * @return true if given string is blank, false - otherwise
   */
  public static boolean isBlank(String str) {
    return str == null || str.isBlank();
  }
}
