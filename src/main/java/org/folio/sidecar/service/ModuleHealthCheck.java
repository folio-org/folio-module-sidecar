package org.folio.sidecar.service;

import io.smallrye.health.checks.UrlHealthCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.HttpMethod;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Readiness;
import org.folio.sidecar.configuration.properties.ModuleProperties;

@ApplicationScoped
@RequiredArgsConstructor
public class ModuleHealthCheck {

  private final PathProcessor pathProcessor;
  private final ModuleProperties moduleProperties;

  @Readiness
  HealthCheck checkModule() {
    return new UrlHealthCheck(getModuleHealthCheckUrl())
      .name("Module health check").requestMethod(HttpMethod.GET).statusCode(200);
  }

  protected String getModuleHealthCheckUrl() {
    var healthUrlBuilder = new StringBuilder(moduleProperties.getUrl());
    var moduleHealthUrlPath = pathProcessor.getModulePath(moduleProperties.getHealthPath());
    healthUrlBuilder.append(moduleHealthUrlPath);
    return healthUrlBuilder.toString();
  }
}
