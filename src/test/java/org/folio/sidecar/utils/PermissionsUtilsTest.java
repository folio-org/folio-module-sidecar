package org.folio.sidecar.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.PERMISSIONS;
import static org.folio.sidecar.utils.PermissionsUtils.extractPermissions;
import static org.folio.sidecar.utils.PermissionsUtils.mergePermissions;
import static org.folio.sidecar.utils.PermissionsUtils.parsePermissionsHeader;
import static org.folio.sidecar.utils.PermissionsUtils.putPermissionsHeaderToRequest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Set;
import org.folio.sidecar.integration.am.model.ModuleBootstrap;
import org.folio.sidecar.integration.am.model.ModuleBootstrapDiscovery;
import org.folio.sidecar.integration.am.model.Permission;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PermissionsUtilsTest {

  @Test
  void putPermissionsHeaderToRequest_positive() {
    var context = mock(RoutingContext.class);
    var request = mock(HttpServerRequest.class);
    var headers = mock(MultiMap.class);
    when(context.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);

    var permissions = List.of("perm1", "perm2");
    putPermissionsHeaderToRequest(context, permissions);

    verify(headers).set(PERMISSIONS, new JsonArray(permissions).encode());
  }

  @Test
  void parsePermissionsHeader_positive() {
    var permissionsHeader = new JsonArray(List.of("perm1", "perm2")).encode();
    var result = parsePermissionsHeader(permissionsHeader);

    assertThat(List.of("perm1", "perm2")).containsExactlyInAnyOrderElementsOf(result);
  }

  @Test
  void parsePermissionsHeader_positive_returnsEmptyListOnInvalidJson() {
    var invalidPermissionsHeader = "invalid_json";
    var result = parsePermissionsHeader(invalidPermissionsHeader);

    assertThat(result).isEmpty();
  }

  @Test
  void mergePermissions_positive_mergesExistingAndNewPermissions() {
    var existingPermissions = List.of("perm1", "perm2");
    var newPermissions = List.of("perm2", "perm3");
    var result = mergePermissions(existingPermissions, newPermissions);

    var expected = Set.of("perm1", "perm2", "perm3");
    assertThat(expected).containsExactlyInAnyOrderElementsOf(result);
  }

  @Test
  void mergePermissions_positive_handlesNullNewPermissions() {
    var existingPermissions = List.of("perm1", "perm2");
    var result = mergePermissions(existingPermissions, null);

    assertThat(existingPermissions).isEqualTo(result);
  }

  @Test
  void extractPermissions_positive_returnsEmptyListWhenPermissionSetsAreEmpty() {
    var moduleBootstrap = new ModuleBootstrap();
    var moduleDescriptor = new ModuleBootstrapDiscovery();
    moduleBootstrap.setModule(moduleDescriptor);

    var result = extractPermissions(moduleBootstrap);

    assertThat(result).isEmpty();
  }

  @Test
  void extractPermissions_positive_returnsPermissionNames() {
    var permission1 = new Permission();
    permission1.setPermissionName("perm1");
    var permission2 = new Permission();
    permission2.setPermissionName("perm2");
    var moduleBootstrap = new ModuleBootstrap();
    var moduleDescriptor = new ModuleBootstrapDiscovery();
    moduleDescriptor.setPermissionSets(List.of(permission1, permission2));
    moduleBootstrap.setModule(moduleDescriptor);

    var result = extractPermissions(moduleBootstrap);

    assertThat(result).containsExactlyInAnyOrder("perm1", "perm2");
  }
}
