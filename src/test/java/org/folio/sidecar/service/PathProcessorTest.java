package org.folio.sidecar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.sidecar.configuration.properties.ModuleProperties;
import org.folio.sidecar.configuration.properties.SidecarProperties;
import org.folio.sidecar.model.ModulePrefixStrategy;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PathProcessorTest {

  @Mock private ModuleProperties moduleProperties;
  @Mock private SidecarProperties sidecarProperties;

  @CsvSource({
    "/mod-foo/users, /mod-foo/users, true, ",
    "/users, /mod-foo/users, true, ",
    "/mod-bar/users, /mod-foo/mod-bar/users, true, ",
    "/mod-foo/users, /mod-foo/users, false, PROXY",
    "/users, /mod-foo/users, false, PROXY",
    "/mod-bar/users, /mod-foo/mod-bar/users, false, PROXY",
  })
  @DisplayName("getModulePath_positive_parameterized_modulePathPrefixEnabled")
  @ParameterizedTest
  void getModulePath_positive_parameterized_modulePathPrefixEnabled(String sourcePath, String expectedPath,
    boolean isModulePrefixEnabled, ModulePrefixStrategy modulePrefixStrategy) {
    when(sidecarProperties.isModulePrefixEnabled()).thenReturn(true);
    when(moduleProperties.getName()).thenReturn("mod-foo");

    var pathProcessor = pathProcessor(isModulePrefixEnabled, modulePrefixStrategy);
    var actual = pathProcessor.getModulePath(sourcePath);

    assertThat(actual).isEqualTo(expectedPath);
  }

  @CsvSource({
    "/mod-foo/users, /users, true, ",
    "/mod-foo/users, /users, false, STRIP",
    "/mod-foo/users, /users, false, PROXY",
    "/users, /users, true, ",
    "/users, /users, true, STRIP",
    "/users, /users, true, PROXY",
    "/mod-bar/users, /mod-bar/users, true, ",
    "/mod-bar/users, /mod-bar/users, true, STRIP",
    "/mod-bar/users, /mod-bar/users, true, PROXY",
    "/mod-foo/mod-bar/users, /mod-bar/users, true, ",
    "/mod-foo/mod-bar/users, /mod-bar/users, true, STRIP",
    "/mod-foo/mod-bar/users, /mod-bar/users, true, PROXY",
  })
  @DisplayName("cleanIngressRequestPath_positive_parameterized_modulePathPrefixEnabled")
  @ParameterizedTest
  void cleanIngressRequestPath_positive_parameterized_modulePathPrefixEnabled(String sourcePath, String expectedPath) {
    when(moduleProperties.getName()).thenReturn("mod-foo");

    var pathProcessor = pathProcessor(true, null);
    var actual = pathProcessor.cleanIngressRequestPath(sourcePath);

    assertThat(actual).isEqualTo(expectedPath);
  }

  @CsvSource({
    "/users, /users, false, ",
    "/users, /users, false, NONE",
    "/users, /users, false, STRIP",
    "/mod-foo/users, /mod-foo/users, false, ",
    "/mod-foo/users, /mod-foo/users, false, NONE",
    "/mod-foo/users, /mod-foo/users, false, STRIP"
  })
  @DisplayName("getModulePath_positive_parameterized_modulePathPrefixDisabled")
  @ParameterizedTest
  void getModulePath_positive_parameterized_modulePathPrefixDisabled(String sourcePath, String expectedPath,
    boolean isModulePrefixEnabled, ModulePrefixStrategy modulePrefixStrategy) {
    var pathProcessor = pathProcessor(isModulePrefixEnabled, modulePrefixStrategy);
    var actual = pathProcessor.getModulePath(sourcePath);

    assertThat(actual).isEqualTo(expectedPath);
  }

  @CsvSource({
    "/users, /users, false, ",
    "/users, /users, false, NONE",
    "/mod-foo/users, /mod-foo/users, false, ",
    "/mod-foo/users, /mod-foo/users, false, NONE"
  })
  @DisplayName("getModulePath_positive_parameterized_modulePathPrefixDisabled")
  @ParameterizedTest
  void cleanIngressRequestPath_positive_parameterized_modulePathPrefixDisabled(String sourcePath, String expectedPath,
    boolean isModulePrefixEnabled, ModulePrefixStrategy modulePrefixStrategy) {
    var pathProcessor = pathProcessor(isModulePrefixEnabled, modulePrefixStrategy);
    var actual = pathProcessor.cleanIngressRequestPath(sourcePath);

    assertThat(actual).isEqualTo(expectedPath);
  }

  private PathProcessor pathProcessor(boolean isModulePrefixEnabled, ModulePrefixStrategy strategy) {
    when(sidecarProperties.isModulePrefixEnabled()).thenReturn(isModulePrefixEnabled);
    if (!isModulePrefixEnabled) {
      when(sidecarProperties.getModulePrefixStrategy()).thenReturn(strategy);
    }

    return new PathProcessor(moduleProperties, sidecarProperties);
  }
}
