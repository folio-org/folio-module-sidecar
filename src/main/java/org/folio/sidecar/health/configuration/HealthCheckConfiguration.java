package org.folio.sidecar.health.configuration;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.health.api.HealthContentFilter;
import jakarta.enterprise.context.ApplicationScoped;
import org.folio.sidecar.health.ModuleHealthCheck;
import org.folio.sidecar.health.RemoveChecksFilter;
import org.folio.sidecar.health.RemoveDataFromNamedCheckFilter;

public class HealthCheckConfiguration {

  private static final String KAFKA_HEALTH_CHECK_NAME = "Kafka connection health check";

  @ApplicationScoped
  @LookupIfProperty(name = "health-check.filter.no-checks.enabled", stringValue = "true")
  public HealthContentFilter noChecksFilter() {
    return new RemoveChecksFilter();
  }

  @ApplicationScoped
  @LookupIfProperty(name = "health-check.filter.kafka-simplified.enabled", stringValue = "true")
  public HealthContentFilter kafkaWithNoDataFilter() {
    return new RemoveDataFromNamedCheckFilter(KAFKA_HEALTH_CHECK_NAME);
  }

  @ApplicationScoped
  @LookupIfProperty(name = "health-check.filter.module-simplified.enabled", stringValue = "true")
  public HealthContentFilter moduleWithNoDataFilter() {
    return new RemoveDataFromNamedCheckFilter(ModuleHealthCheck.CHECK_NAME);
  }
}
