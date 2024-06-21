package org.folio.sidecar.service;

import static org.folio.sidecar.model.ModulePrefixStrategy.PROXY;
import static org.folio.sidecar.model.ModulePrefixStrategy.STRIP;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.model.ModulePrefixStrategy;

@ApplicationScoped
public class PathProcessor {

  private final String modulePathPrefix;
  private final ModulePrefixStrategy modulePrefixStrategy;

  /**
   * Injects dependencies from quarkus context.
   *
   * @param moduleProperties - underlying module configuration
   * @param scProperties - sidecar properties
   */
  @Inject
  public PathProcessor(ModuleProperties moduleProperties, SidecarProperties scProperties) {
    this.modulePrefixStrategy = scProperties.isModulePrefixEnabled() ? PROXY : scProperties.getModulePrefixStrategy();
    this.modulePathPrefix = '/' + moduleProperties.getName();
  }

  /**
   * Builds path with {@code "/$moduleName"} prefix if it's defined by configuration.
   *
   * @param path - URL path
   * @return updated path as {@link String} value with module name prefix (according to configuration)
   */
  public String getModulePath(String path) {
    if (modulePrefixStrategy == PROXY) {
      return !path.startsWith(modulePathPrefix) ? modulePathPrefix + path : path;
    }

    if (modulePrefixStrategy == STRIP) {
      return cleanIngressRequestPath(path);
    }

    return path;
  }

  /**
   * Updates path removing {@code "/$moduleName"} prefix if it's defined by configuration.
   *
   * @param path - URL path
   * @return updated path value without module name prefix (according to configuration)
   */
  public String cleanIngressRequestPath(String path) {
    if (modulePrefixStrategy == PROXY || modulePrefixStrategy == STRIP) {
      return path.startsWith(modulePathPrefix) ? path.substring(modulePathPrefix.length()) : path;
    }

    return path;
  }
}
