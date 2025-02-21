package org.folio.sidecar.configuration.properties;

import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.spi.Converter;

public interface CacheSettings {

  OptionalInt initialCapacity();

  OptionalInt maxSize();

  Optional<Duration> expireAfterWrite();

  Optional<Duration> expireAfterAccess();

  interface Duration {

    @WithDefault("0")
    long duration();

    @WithDefault("secs")
    @WithConverter(TimeUnitConverter.class)
    TimeUnit unit();

    class TimeUnitConverter implements Converter<TimeUnit> {

      @Override
      public TimeUnit convert(String value) throws IllegalArgumentException, NullPointerException {
        var v = value.toLowerCase();
        return switch (v) {
          case "nanos" -> TimeUnit.NANOSECONDS;
          case "micros" -> TimeUnit.MICROSECONDS;
          case "millis" -> TimeUnit.MILLISECONDS;
          case "secs" -> TimeUnit.SECONDS;
          case "mins" -> TimeUnit.MINUTES;
          case "hours" -> TimeUnit.HOURS;
          case "days" -> TimeUnit.DAYS;
          default -> throw new IllegalArgumentException("Unknown time unit: " + value);
        };
      }
    }
  }
}
