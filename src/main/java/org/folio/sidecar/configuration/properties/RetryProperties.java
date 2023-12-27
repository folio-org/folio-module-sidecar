package org.folio.sidecar.configuration.properties;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Data
@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor
public class RetryProperties {

  @ConfigProperty(name = "retry.attempts") Integer attempts;
  @ConfigProperty(name = "retry.min-delay") Duration minDelay;
  @ConfigProperty(name = "retry.max-delay") Duration maxDelay;
  @ConfigProperty(name = "retry.back-off-factor") Integer backOffFactor;
}
