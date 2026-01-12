package org.folio.sidecar.utils;

import static java.util.Arrays.asList;
import static java.util.Comparator.comparingInt;

import jakarta.enterprise.inject.Instance;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.sidecar.support.Ordered;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionUtils {

  /**
   * Checks if given collection is null or empty.
   *
   * @param collection - {@link Collection} to check
   * @return true if collection is null or empty, false - otherwise
   */
  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  /**
   * Checks if given {@link Map} is null or empty.
   *
   * @param map - {@link Map} to check
   * @return true if {@link Map} is null or empty, false - otherwise
   */
  public static boolean isEmpty(Map<?, ?> map) {
    return map == null || map.isEmpty();
  }

  /**
   * Checks if given collection is not null or empty.
   *
   * @param collection - {@link Collection} to check
   * @return true if {@link Collection} is not null or empty, false - otherwise
   */
  public static boolean isNotEmpty(Collection<?> collection) {
    return collection != null && !collection.isEmpty();
  }

  /**
   * Converts array to immutable list.
   *
   * @param array - array of {@link T} objects
   * @param <T> - generic type for list elements
   * @return given array as list, {@link Collections#emptyList()} - otherwise.
   */
  public static <T> List<T> toList(T[] array) {
    return array == null ? Collections.emptyList() : asList(array);
  }

  public static <T> List<T> safeList(List<T> list) {
    return list != null ? list : Collections.emptyList();
  }

  /**
   * Returns a stream from nullable collection.
   *
   * @param source - nullable {@link Collection} object
   * @param <T> - generic type for collection element
   * @return a stream from nullable collection
   */
  public static <T> Stream<T> toStream(Collection<T> source) {
    return isEmpty(source) ? Stream.empty() : source.stream();
  }

  /**
   * Sorts {@link Instance} of {@link Ordered} objects by its order.
   *
   * @param values - {@link Instance} of {@link Ordered} objects
   * @param <T> - generic type for {@link Instance} elements
   * @return sorted by order {@link List} of {@link Ordered} objects
   */
  public static <T extends Ordered> List<T> sortByOrder(Instance<T> values) {
    return values.stream().sorted(comparingInt(Ordered::getOrder)).toList();
  }

  /**
   * Takes one element from the given collection. If the collection is empty or contains more than one element,
   * an exception is thrown using the provided suppliers.
   *
   * @param source                     - the source collection
   * @param emptyCollectionExcSupplier - supplier for exception to be thrown if the collection is empty
   * @param tooManyItemsExcSupplier    - supplier for exception to be thrown if the collection has more than one element
   * @param <T>                        - generic type for collection element
   * @return the single element from the collection
   * @throws RuntimeException if the collection is empty or has more than one element
   */
  public static <T> T takeOne(Collection<T> source, Supplier<? extends RuntimeException> emptyCollectionExcSupplier,
    Supplier<? extends RuntimeException> tooManyItemsExcSupplier) {
    if (isEmpty(source)) {
      throw emptyCollectionExcSupplier.get();
    }

    if (source.size() > 1) {
      throw tooManyItemsExcSupplier.get();
    }

    return source.iterator().next();
  }
}
