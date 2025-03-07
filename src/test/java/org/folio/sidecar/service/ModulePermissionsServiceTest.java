package org.folio.sidecar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class ModulePermissionsServiceTest {

  private ModulePermissionsService modulePermissionsService;

  @BeforeEach
  void setUp() {
    modulePermissionsService = new ModulePermissionsService();
  }

  @Test
  void getPermissions_returnsEmptyListInitially() {
    var result = modulePermissionsService.getPermissions().result();
    assertTrue(result.isEmpty());
  }

  @Test
  void putPermissions_updatesPermissionsCache() {
    var permissions = Set.of("perm1", "perm2");
    modulePermissionsService.putPermissions(permissions).result();

    var result = modulePermissionsService.getPermissions().result();
    assertEquals(permissions, result);
  }

  @Test
  void putPermissions_handlesEmptyPermissionsList() {
    modulePermissionsService.putPermissions(Set.of()).result();

    var result = modulePermissionsService.getPermissions().result();
    assertTrue(result.isEmpty());
  }

  @Test
  void getPermissions_positive_returnsUpdatedPermissions() {
    var initialPermissions = Set.of("perm1");
    var updatedPermissions = Set.of("perm1", "perm2");

    modulePermissionsService.putPermissions(initialPermissions).result();
    modulePermissionsService.putPermissions(updatedPermissions).result();

    var result = modulePermissionsService.getPermissions().result();
    assertEquals(updatedPermissions, result);
  }
}
