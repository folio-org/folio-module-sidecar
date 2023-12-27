package org.folio.sidecar.service;

import io.smallrye.health.checks.UrlHealthCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.HttpMethod;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Readiness;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;

@ApplicationScoped
@RequiredArgsConstructor
public class ModuleHealthCheck {

  private final ModuleProperties moduleProperties;
  private final SidecarProperties sidecarProperties;

  @Readiness
  HealthCheck checkModule() {
    return new UrlHealthCheck(getModuleHealthCheckUrl())
      .name("Module health check").requestMethod(HttpMethod.GET).statusCode(200);
  }

  protected String getModuleHealthCheckUrl() {
    var healthUrlBuilder = new StringBuilder(moduleProperties.getUrl());
    if (sidecarProperties.isModulePrefixEnabled()) {
      healthUrlBuilder.append('/').append(moduleProperties.getName());
    }
    healthUrlBuilder.append(moduleProperties.getHealthPath());
    return healthUrlBuilder.toString();
  }
}
