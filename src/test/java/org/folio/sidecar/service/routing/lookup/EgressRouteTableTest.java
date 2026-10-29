package org.folio.sidecar.service.routing.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.sidecar.support.TestConstants.MODULE_BOOTSTRAP_EGRESS;

import java.util.List;
import java.util.Map;
import org.folio.sidecar.model.ScRoutingEntry;
import org.folio.support.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class EgressRouteTableTest {

  private static final String MOD_BAR = "mod-bar-0.5.1";
  private static final String MOD_BAZ = "mod-baz-0.5.1";
  private static final String NEW_LOCATION = "http://new-location:8081";

  @Test
  void of_positive_collectsRoutesAndModuleIds() {
    var table = table();

    assertThat(table.moduleIds()).containsExactlyInAnyOrder(MOD_BAR, MOD_BAZ);
    assertThat(table.routes()).isNotEmpty();
    assertThat(table.removedModuleIds()).isEmpty();
  }

  @Test
  void empty_positive_hasNoRoutesOrModules() {
    var table = EgressRouteTable.empty();

    assertThat(table.routes()).isEmpty();
    assertThat(table.moduleIds()).isEmpty();
    assertThat(table.removedModuleIds()).isEmpty();
  }

  @Test
  void withLocation_positive_onlyTargetModuleRelocated() {
    var updated = table().withLocation(MOD_BAR, NEW_LOCATION);

    var barLocations = locations(updated, MOD_BAR);
    var bazLocations = locations(updated, MOD_BAZ);
    assertThat(barLocations).isNotEmpty().containsOnly(NEW_LOCATION);
    assertThat(bazLocations).isNotEmpty().doesNotContain(NEW_LOCATION);
  }

  @Test
  void withLocation_positive_originalTableUnchanged() {
    var original = table();

    original.withLocation(MOD_BAR, NEW_LOCATION);

    assertThat(locations(original, MOD_BAR)).doesNotContain(NEW_LOCATION);
  }

  @Test
  void withoutModule_positive_removesRoutesAndRemembersId() {
    var updated = table().withoutModule(MOD_BAR);

    assertThat(updated.containsModule(MOD_BAR)).isFalse();
    assertThat(updated.isRemoved(MOD_BAR)).isTrue();
    assertThat(updated.containsModule(MOD_BAZ)).isTrue();
    assertThat(locations(updated, MOD_BAR)).isEmpty();
    assertThat(locations(updated, MOD_BAZ)).isNotEmpty();
  }

  @Test
  void withoutModule_positive_dropsEmptyPrefixes() {
    var original = table();
    var barOnlyPrefixes = original.routes().entrySet().stream()
      .filter(entry -> entry.getValue().stream().allMatch(route -> MOD_BAR.equals(route.getModuleId())))
      .map(Map.Entry::getKey)
      .toList();

    var updated = original.withoutModule(MOD_BAR);

    assertThat(barOnlyPrefixes).isNotEmpty();
    assertThat(updated.routes().keySet()).doesNotContainAnyElementsOf(barOnlyPrefixes);
  }

  private static EgressRouteTable table() {
    return EgressRouteTable.of(MODULE_BOOTSTRAP_EGRESS.getRequiredModules());
  }

  private static List<String> locations(EgressRouteTable table, String moduleId) {
    return table.routes().values().stream()
      .flatMap(List::stream)
      .filter(route -> moduleId.equals(route.getModuleId()))
      .map(ScRoutingEntry::getLocation)
      .toList();
  }
}
