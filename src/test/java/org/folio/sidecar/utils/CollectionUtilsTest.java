package org.folio.sidecar.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptyNavigableMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.folio.sidecar.support.Ordered;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class CollectionUtilsTest {

  @ParameterizedTest
  @MethodSource("emptyCollectionDataProvider")
  void isEmptyCollection_positive_parameterized(Collection<?> collection) {
    var result = CollectionUtils.isEmpty(collection);
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyCollectionDataProvider")
  void isEmptyCollection_negative_parameterized(Collection<?> collection) {
    var result = CollectionUtils.isEmpty(collection);
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyCollectionDataProvider")
  void isNotEmptyCollection_positive_parameterized(Collection<?> collection) {
    var result = CollectionUtils.isNotEmpty(collection);
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @MethodSource("emptyCollectionDataProvider")
  void isNotEmptyCollection_negative_parameterized(Collection<?> collection) {
    var result = CollectionUtils.isNotEmpty(collection);
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @MethodSource("emptyMapDataProvider")
  void isEmptyMap_positive_parameterized(Map<?, ?> map) {
    var result = CollectionUtils.isEmpty(map);
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyMapDataProvider")
  void isEmptyMap_negative_parameterized(Map<?, ?> map) {
    var result = CollectionUtils.isEmpty(map);
    assertThat(result).isFalse();
  }

  @Test
  void toList_positive() {
    var result = CollectionUtils.toList(new String[] {"1", "2"});
    assertThat(result).isEqualTo(List.of("1", "2"));
  }

  @Test
  void toList_positive_null() {
    var result = CollectionUtils.toList(null);
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void sortByOrder_positive() {
    var ordered1 = (Ordered) () -> 100;
    var ordered2 = (Ordered) () -> 2;
    var ordered3 = (Ordered) () -> -100;

    var instance = (Instance<Ordered>) mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(ordered1, ordered2, ordered3));

    var list = CollectionUtils.sortByOrder(instance);
    assertThat(list).isEqualTo(List.of(ordered3, ordered2, ordered1));
  }

  @ParameterizedTest
  @MethodSource("takeOnePositiveDataProvider")
  void takeOne_positive_returnsSingleElement(Collection<?> collection, Object expectedElement) {
    Supplier<RuntimeException> emptyExceptionSupplier = () -> new IllegalStateException("Collection is empty");
    Supplier<RuntimeException> tooManyExceptionSupplier = () -> new IllegalStateException("Too many elements");

    var result = CollectionUtils.takeOne(collection, emptyExceptionSupplier, tooManyExceptionSupplier);

    assertThat(result).isEqualTo(expectedElement);
  }

  @ParameterizedTest
  @MethodSource("takeOneEmptyCollectionDataProvider")
  void takeOne_negative_throwsExceptionForEmptyCollection(Collection<?> collection) {
    Supplier<RuntimeException> emptyExceptionSupplier =
      () -> new IllegalArgumentException("Collection must not be empty");
    Supplier<RuntimeException> tooManyExceptionSupplier = () -> new IllegalStateException("Too many elements");

    assertThatThrownBy(() -> CollectionUtils.takeOne(collection, emptyExceptionSupplier, tooManyExceptionSupplier))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Collection must not be empty");
  }

  @ParameterizedTest
  @MethodSource("takeOneMultipleElementsDataProvider")
  void takeOne_negative_throwsExceptionForMultipleElements(Collection<?> collection) {
    Supplier<RuntimeException> emptyExceptionSupplier = () -> new IllegalStateException("Collection is empty");
    Supplier<RuntimeException> tooManyExceptionSupplier =
      () -> new UnsupportedOperationException("Expected exactly one element");

    assertThatThrownBy(() -> CollectionUtils.takeOne(collection, emptyExceptionSupplier, tooManyExceptionSupplier))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Expected exactly one element");
  }

  private static Stream<Arguments> emptyCollectionDataProvider() {
    return Stream.of(
      arguments((List<?>) null),
      arguments(emptyList()),
      arguments(emptySet()),
      arguments(new ArrayList<>()),
      arguments(new LinkedHashSet<>())
    );
  }

  private static Stream<Arguments> nonEmptyCollectionDataProvider() {
    return Stream.of(
      arguments(List.of(1, 2, 3)),
      arguments(List.of("str1", "str2")),
      arguments(Set.of(2, 1)),
      arguments(Set.of("s1", "s2"))
    );
  }

  private static Stream<Arguments> emptyMapDataProvider() {
    return Stream.of(
      arguments((Map<?, ?>) null),
      arguments(emptyMap()),
      arguments(emptyNavigableMap()),
      arguments(new HashMap<>()),
      arguments(new LinkedHashMap<>(20, 1.2f))
    );
  }

  private static Stream<Arguments> nonEmptyMapDataProvider() {
    return Stream.of(
      arguments(singletonMap("key", "value")),
      arguments(singletonMap(1, 2)),
      arguments(Map.of(1, 1, "key", "value"))
    );
  }

  private static Stream<Arguments> takeOnePositiveDataProvider() {
    return Stream.of(
      arguments(List.of("single"), "single"),
      arguments(List.of(42), 42),
      arguments(Set.of("element"), "element"),
      arguments(List.of(true), true),
      arguments(Set.of(100L), 100L)
    );
  }

  private static Stream<Arguments> takeOneEmptyCollectionDataProvider() {
    return Stream.of(
      arguments((List<?>) null),
      arguments(emptyList()),
      arguments(emptySet()),
      arguments(new ArrayList<>()),
      arguments(new LinkedHashSet<>())
    );
  }

  private static Stream<Arguments> takeOneMultipleElementsDataProvider() {
    return Stream.of(
      arguments(List.of("first", "second")),
      arguments(List.of(1, 2, 3)),
      arguments(Set.of("a", "b")),
      arguments(List.of(true, false)),
      arguments(Set.of(1, 2, 3, 4, 5))
    );
  }
}
