# MODSIDECAR-198 Wildcard `permissionsRequired` Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the sidecar treat `permissionsRequired: ["*"]` as "valid token required, no named permission" while keeping `permissionsRequired: []` truly public.

**Architecture:** Introduce three intention-revealing predicates in `RoutingUtils` (`isTrulyPublic`, `isWildcardPermissionRequired`, `requiresNoNamedPermission`) and point each Keycloak ingress filter at the precise one. Only the authorization filter changes behavior (skip RPT for wildcard); the JWT and tenant filters switch to `isTrulyPublic`, which is byte-for-byte equivalent to the old `hasNoPermissionsRequired` and keeps requiring token + tenant for `["*"]`.

**Tech Stack:** Java 21, Quarkus 3.36, Vert.x Web `RoutingContext`, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Java 21; production methods ≤ 23 lines (checkstyle `process-classes`, fails on any warning incl. tests).
- Never use lenient Mockito; verify only unmocked interactions; prefer helper methods over blanket `@BeforeEach` stubbing.
- Test naming: `methodName_positive/negative_description`.
- Wildcard convention is the **exact singleton** `["*"]` only; mixed lists like `["*","x"]` are NOT wildcard (AC6).
- Commit message format: `MODSIDECAR-198 - <summary>`.
- Run unit tests with: `mvn test -Dgroups=unit -Dtest="<Class>"`. Checkstyle: `mvn checkstyle:check`.

---

### Task 1: `RoutingUtils` permission predicates

**Files:**
- Modify: `src/main/java/org/folio/sidecar/utils/RoutingUtils.java` (around line 179, `hasNoPermissionsRequired`)
- Test: `src/test/java/org/folio/sidecar/utils/RoutingUtilsTest.java`

**Interfaces:**
- Consumes: `getScRoutingEntry(rc)` → `ScRoutingEntry`; `ScRoutingEntry.getRoutingEntry()` → `ModuleBootstrapEndpoint`; `ModuleBootstrapEndpoint.getPermissionsRequired()` → `List<String>` (nullable); `CollectionUtils.isEmpty(Collection)`.
- Produces: `public static final String WILDCARD_PERMISSION = "*"`; `boolean isTrulyPublic(RoutingContext)`; `boolean isWildcardPermissionRequired(RoutingContext)`; `boolean requiresNoNamedPermission(RoutingContext)`. (`hasNoPermissionsRequired` temporarily delegates to `isTrulyPublic`; removed in Task 4.)

**Test-first:** yes — the three new helpers do not exist, so the new tests fail to compile/run until they are added.

- [ ] **Step 1: Write the failing tests**

Add to `RoutingUtilsTest` (new imports: `java.util.List`, `org.folio.sidecar.integration.am.model.ModuleBootstrapEndpoint`, `org.folio.sidecar.model.ScRoutingEntry`):

```java
  @Test
  void isTrulyPublic_positive_emptyPermissions() {
    assertThat(RoutingUtils.isTrulyPublic(rcWithPermissions(List.of()))).isTrue();
  }

  @Test
  void isTrulyPublic_positive_nullPermissions() {
    assertThat(RoutingUtils.isTrulyPublic(rcWithPermissions(null))).isTrue();
  }

  @Test
  void isTrulyPublic_negative_namedPermission() {
    assertThat(RoutingUtils.isTrulyPublic(rcWithPermissions(List.of("foo.item.get")))).isFalse();
  }

  @Test
  void isTrulyPublic_negative_wildcard() {
    assertThat(RoutingUtils.isTrulyPublic(rcWithPermissions(List.of("*")))).isFalse();
  }

  @Test
  void isWildcardPermissionRequired_positive_singletonWildcard() {
    assertThat(RoutingUtils.isWildcardPermissionRequired(rcWithPermissions(List.of("*")))).isTrue();
  }

  @Test
  void isWildcardPermissionRequired_negative_mixedList() {
    assertThat(RoutingUtils.isWildcardPermissionRequired(rcWithPermissions(List.of("*", "foo.item.get")))).isFalse();
  }

  @Test
  void isWildcardPermissionRequired_negative_emptyList() {
    assertThat(RoutingUtils.isWildcardPermissionRequired(rcWithPermissions(List.of()))).isFalse();
  }

  @Test
  void isWildcardPermissionRequired_negative_namedPermission() {
    assertThat(RoutingUtils.isWildcardPermissionRequired(rcWithPermissions(List.of("foo.item.get")))).isFalse();
  }

  @Test
  void requiresNoNamedPermission_positive_trulyPublic() {
    assertThat(RoutingUtils.requiresNoNamedPermission(rcWithPermissions(List.of()))).isTrue();
  }

  @Test
  void requiresNoNamedPermission_positive_wildcard() {
    assertThat(RoutingUtils.requiresNoNamedPermission(rcWithPermissions(List.of("*")))).isTrue();
  }

  @Test
  void requiresNoNamedPermission_negative_namedPermission() {
    assertThat(RoutingUtils.requiresNoNamedPermission(rcWithPermissions(List.of("foo.item.get")))).isFalse();
  }

  @Test
  void requiresNoNamedPermission_negative_mixedList() {
    assertThat(RoutingUtils.requiresNoNamedPermission(rcWithPermissions(List.of("*", "foo.item.get")))).isFalse();
  }

  private static RoutingContext rcWithPermissions(List<String> permissionsRequired) {
    var endpoint = new ModuleBootstrapEndpoint("/foo/items", "GET");
    endpoint.setPermissionsRequired(permissionsRequired);
    var entry = ScRoutingEntry.of("mod-foo-1.0", "http://mod-foo", "mod-foo-api-1.0", endpoint);
    var rc = mock(RoutingContext.class);
    when(rc.get(RoutingUtils.SC_ROUTING_ENTRY_KEY)).thenReturn(entry);
    return rc;
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dgroups=unit -Dtest="RoutingUtilsTest"`
Expected: FAIL — compilation error, `isTrulyPublic`/`isWildcardPermissionRequired`/`requiresNoNamedPermission` not found.

- [ ] **Step 3: Add the predicates to `RoutingUtils`**

Add the constant near the other constants (after line 48):

```java
  public static final String WILDCARD_PERMISSION = "*";
```

Replace the existing `hasNoPermissionsRequired` method (line 179) with:

```java
  /**
   * Truly public endpoint: no token required (e.g. forgotten-password). {@code permissionsRequired} is null/empty.
   */
  public static boolean isTrulyPublic(RoutingContext rc) {
    var endpoint = getScRoutingEntry(rc).getRoutingEntry();
    return isEmpty(endpoint.getPermissionsRequired());
  }

  /**
   * Wildcard-authenticated endpoint: a valid token is required but no named permission. The convention is the
   * exact singleton {@code ["*"]}; a mixed list such as {@code ["*", "perm"]} is NOT wildcard.
   */
  public static boolean isWildcardPermissionRequired(RoutingContext rc) {
    var permissionsRequired = getScRoutingEntry(rc).getRoutingEntry().getPermissionsRequired();
    return permissionsRequired != null && permissionsRequired.size() == 1
      && WILDCARD_PERMISSION.equals(permissionsRequired.get(0));
  }

  /**
   * No specific named permission to evaluate: either truly public or wildcard-authenticated.
   */
  public static boolean requiresNoNamedPermission(RoutingContext rc) {
    return isTrulyPublic(rc) || isWildcardPermissionRequired(rc);
  }

  /**
   * @deprecated transitional alias for {@link #isTrulyPublic(RoutingContext)}; removed once all filters migrate.
   */
  @Deprecated
  public static boolean hasNoPermissionsRequired(RoutingContext rc) {
    return isTrulyPublic(rc);
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dgroups=unit -Dtest="RoutingUtilsTest"`
Expected: PASS (all new + existing tests green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/sidecar/utils/RoutingUtils.java src/test/java/org/folio/sidecar/utils/RoutingUtilsTest.java
git commit -m "MODSIDECAR-198 - Add truly-public/wildcard permission predicates to RoutingUtils"
```

---

### Task 2: Authorization filter skips RPT for wildcard (behavior change)

**Files:**
- Modify: `src/main/java/org/folio/sidecar/integration/keycloak/filter/KeycloakAuthorizationFilter.java` (import line 17; `shouldSkip` line 80)
- Test: `src/test/java/org/folio/sidecar/integration/keycloak/filter/KeycloakAuthorizationFilterTest.java`

**Interfaces:**
- Consumes: `RoutingUtils.requiresNoNamedPermission(RoutingContext)` from Task 1.
- Produces: none (internal filter change).

**Test-first:** yes — failing test: `shouldSkip` for a `["*"]` endpoint currently returns `false` (RPT runs); after the change it must return `true`.

- [ ] **Step 1: Write the failing tests**

Add to `KeycloakAuthorizationFilterTest` (uses existing `scRoutingEntry(...)` from `AbstractFilterTest`, `RETURNS_DEEP_STUBS`, `SC_ROUTING_ENTRY_KEY`):

```java
  @Test
  void shouldSkip_positive_wildcardPermission() {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry("not-system", "*"));

    var result = keycloakAuthorizationFilter.shouldSkip(routingContext);

    assertThat(result).isTrue();
  }

  @Test
  void shouldSkip_negative_wildcardWithNamedPermission() {
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.get(SC_ROUTING_ENTRY_KEY)).thenReturn(scRoutingEntry("not-system", "*", "foo.item.get"));

    var result = keycloakAuthorizationFilter.shouldSkip(routingContext);

    assertThat(result).isFalse();
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dgroups=unit -Dtest="KeycloakAuthorizationFilterTest"`
Expected: FAIL — `shouldSkip_positive_wildcardPermission` expects `true` but gets `false` (RPT not yet skipped for wildcard).

- [ ] **Step 3: Swap the predicate**

In `KeycloakAuthorizationFilter.java`, change the static import (line 17):

```java
import static org.folio.sidecar.utils.RoutingUtils.requiresNoNamedPermission;
```

and `shouldSkip` (line 80):

```java
  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return !isTimerRequest(rc) && (isSystemRequest(rc) || requiresNoNamedPermission(rc)) || isSelfRequest(rc);
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dgroups=unit -Dtest="KeycloakAuthorizationFilterTest"`
Expected: PASS (new wildcard tests + existing named/public/system/self/timer tests green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/sidecar/integration/keycloak/filter/KeycloakAuthorizationFilter.java src/test/java/org/folio/sidecar/integration/keycloak/filter/KeycloakAuthorizationFilterTest.java
git commit -m "MODSIDECAR-198 - Skip Keycloak RPT evaluation for wildcard-authenticated endpoints"
```

---

### Task 3: Tenant filter uses `isTrulyPublic`; lock wildcard tenant validation

**Files:**
- Modify: `src/main/java/org/folio/sidecar/integration/keycloak/filter/KeycloakTenantFilter.java` (import line 10; `shouldSkip` line 59)
- Test: `src/test/java/org/folio/sidecar/integration/keycloak/filter/KeycloakTenantFilterTest.java`

**Interfaces:**
- Consumes: `RoutingUtils.isTrulyPublic(RoutingContext)` from Task 1.
- Produces: none.

**Test-first:** no — the production edit is a clarity rename (`isTrulyPublic` ≡ old `hasNoPermissionsRequired`); wildcard already validated tenant because `["*"]` is non-empty. The added tests are AC3/AC4 regression locks that pass before and after; they guard against future drift.

- [ ] **Step 1: Swap the predicate**

In `KeycloakTenantFilter.java`, change the static import (line 10):

```java
import static org.folio.sidecar.utils.RoutingUtils.isTrulyPublic;
```

and `shouldSkip` (line 59):

```java
  @Override
  public boolean shouldSkip(RoutingContext rc) {
    return !isTimerRequest(rc) && (isSystemRequest(rc) || isTrulyPublic(rc)) || isSelfRequest(rc);
  }
```

- [ ] **Step 2: Add regression tests**

Add to `KeycloakTenantFilterTest` (uses existing `routingContext(...)`, `scRoutingEntry(...)`, `keycloakIssuer(...)`, `TENANT_NAME`, `PARSED_TOKEN`, `SYSTEM_TOKEN`, `TENANT`):

```java
  @Test
  void shouldSkip_negative_wildcardPermission() {
    var routingContext = routingContext(scRoutingEntry("not-system", "*"), rc -> {});
    var actual = keycloakTenantFilter.shouldSkip(routingContext);
    assertThat(actual).isFalse();
  }

  @Test
  void filter_positive_wildcardTenantMatches() {
    var accessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry("not-system", "*"), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(PARSED_TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer(TENANT_NAME));
      when(rc.request().getHeader(TENANT)).thenReturn(TENANT_NAME);
    });
    when(sidecarProperties.isCrossTenantEnabled()).thenReturn(false);

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void filter_negative_wildcardTenantMismatch() {
    var accessToken = mock(JsonWebToken.class);
    var routingContext = routingContext(scRoutingEntry("not-system", "*"), rc -> {
      when(rc.get(RoutingUtils.SELF_REQUEST_KEY)).thenReturn(false);
      when(rc.get(PARSED_TOKEN)).thenReturn(accessToken);
      when(rc.get(SYSTEM_TOKEN)).thenReturn(null);
      when(accessToken.getIssuer()).thenReturn(keycloakIssuer("test-tenant-a"));
      when(rc.request().getHeader(TENANT)).thenReturn("test-tenant-b");
    });
    when(sidecarProperties.isCrossTenantEnabled()).thenReturn(false);

    var result = keycloakTenantFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("X-Okapi-Tenant header is not the same as resolved tenant");
  }
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `mvn test -Dgroups=unit -Dtest="KeycloakTenantFilterTest"`
Expected: PASS (wildcard validation runs for `["*"]`; existing tests green).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/folio/sidecar/integration/keycloak/filter/KeycloakTenantFilter.java src/test/java/org/folio/sidecar/integration/keycloak/filter/KeycloakTenantFilterTest.java
git commit -m "MODSIDECAR-198 - Use isTrulyPublic in tenant filter and cover wildcard tenant validation"
```

---

### Task 4: JWT filter uses `isTrulyPublic`; lock wildcard token enforcement; remove dead alias

**Files:**
- Modify: `src/main/java/org/folio/sidecar/integration/keycloak/filter/KeycloakJwtFilter.java` (import line 13; `handleFailedTokenParsing` line 125)
- Modify: `src/main/java/org/folio/sidecar/utils/RoutingUtils.java` (remove the transitional `hasNoPermissionsRequired`)
- Test: `src/test/java/org/folio/sidecar/integration/keycloak/filter/KeycloakJwtFilterTest.java`

**Interfaces:**
- Consumes: `RoutingUtils.isTrulyPublic(RoutingContext)` from Task 1.
- Produces: none. After this task `hasNoPermissionsRequired` no longer exists.

**Test-first:** no — the JWT production edit is a clarity rename (`isTrulyPublic` ≡ old `hasNoPermissionsRequired`); `["*"]` already required a token because it is non-empty. Added tests are AC2/FR4 regression locks (green before and after). The alias removal is a safe dead-code deletion guarded by compilation + the full suite.

- [ ] **Step 1: Swap the predicate**

In `KeycloakJwtFilter.java`, change the static import (line 13):

```java
import static org.folio.sidecar.utils.RoutingUtils.isTrulyPublic;
```

and in `handleFailedTokenParsing` (line 125) replace `hasNoPermissionsRequired(rc)` with `isTrulyPublic(rc)`:

```java
    if (isTrulyPublic(rc) && !Objects.equals(FAILED_TO_PARSE_JWT_ERROR_MSG, error.getMessage())
      || isSelfRequest(rc) && !hasToken(rc)
      // If system token is present, then we should not fail the request
      || getParsedSystemToken(rc).isPresent()) {
      return succeededFuture(rc);
    }
```

- [ ] **Step 2: Add regression tests**

Add to `KeycloakJwtFilterTest` (uses existing `routingContext(...)`, `scRoutingEntry(...)`, `headers(...)`, `request`, `AUTH_TOKEN`, `TOKEN`, `asyncJsonWebTokenParser`, `ParseException`):

```java
  // AC1 positive path (truly public): [] + missing token must be ALLOWED.
  // Pairs with filter_negative_wildcardMissingToken below to demonstrate the [] vs ["*"] difference (FR10).
  @Test
  void filter_positive_publicMissingToken() {
    var requestHeaders = headers(Collections.emptyMap());
    var routingContext = routingContext(scRoutingEntry("not-system"), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(routingContext);
  }

  @Test
  void filter_negative_wildcardMissingToken() {
    var requestHeaders = headers(Collections.emptyMap());
    var routingContext = routingContext(scRoutingEntry("not-system", "*"), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Failed to find JWT in request");
  }

  @Test
  void filter_negative_wildcardInvalidToken() {
    var requestHeaders = headers(Map.of(TOKEN, AUTH_TOKEN));
    var routingContext = routingContext(scRoutingEntry("not-system", "*"), rc -> {
      when(rc.request()).thenReturn(request);
      when(request.headers()).thenReturn(requestHeaders);
    });

    when(asyncJsonWebTokenParser.parseAsync(AUTH_TOKEN)).thenReturn(Future.failedFuture(
      new UnauthorizedException("Failed to parse JWT", new ParseException("Failed to parse JWT, invalid offset"))));

    var result = keycloakJwtFilter.applyFilter(routingContext);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.cause())
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Failed to parse JWT");
  }
```

- [ ] **Step 3: Remove the transitional alias**

In `RoutingUtils.java`, delete the `@Deprecated hasNoPermissionsRequired(...)` method added in Task 1. Confirm no references remain:

Run: `grep -rn "hasNoPermissionsRequired" src/`
Expected: no matches.

- [ ] **Step 4: Run the full unit suite + checkstyle**

Run: `mvn test -Dgroups=unit`
Expected: PASS (whole suite green).

Run: `mvn checkstyle:check`
Expected: PASS (no warnings; all new methods ≤ 23 lines).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/sidecar/integration/keycloak/filter/KeycloakJwtFilter.java src/main/java/org/folio/sidecar/utils/RoutingUtils.java src/test/java/org/folio/sidecar/integration/keycloak/filter/KeycloakJwtFilterTest.java
git commit -m "MODSIDECAR-198 - Use isTrulyPublic in JWT filter and remove dead hasNoPermissionsRequired alias"
```

---

## Self-Review

**Spec coverage:**
- AC1 (`[]` public) — Task 4 `filter_positive_publicMissingToken` explicitly locks `[]` + missing token → allowed (the truly-public failed-parse tolerance branch, which no pre-existing JWT test covered). `isTrulyPublic` == old predicate, so the rename is behavior-preserving; the new test is the regression lock. Paired with `filter_negative_wildcardMissingToken` it demonstrates the `[]` vs `["*"]` difference (FR10). ✓
- AC2 (`["*"]` no token → 401) — Task 4 `filter_negative_wildcardMissingToken`. ✓
- AC3 (`["*"]` valid token → parse+tenant run, RPT skipped) — Task 2 `shouldSkip_positive_wildcardPermission` + Task 3 `filter_positive_wildcardTenantMatches`. ✓
- AC4 (`["*"]` tenant mismatch → reject) — Task 3 `filter_negative_wildcardTenantMismatch`. ✓
- AC5 (named perms unchanged) — existing `shouldSkip_positive()` (named → false) + Task 2 `shouldSkip_negative_wildcardWithNamedPermission`. ✓
- AC6 (`["*","x"]` not wildcard) — Task 1 `isWildcardPermissionRequired_negative_mixedList` + Task 2 `shouldSkip_negative_wildcardWithNamedPermission`. ✓
- FR3 (clear helpers) — Task 1. FR4 (missing/invalid token) — Task 4 both tests. FR7 (skip RPT) — Task 2. FR8 (system/timer/self unchanged) — guards untouched; existing system/self/timer tests green. FR10 (tests for `[]` vs `["*"]` vs named) — covered across tasks.

**Placeholder scan:** none — every step has concrete code/commands.

**Type consistency:** helper names (`isTrulyPublic`, `isWildcardPermissionRequired`, `requiresNoNamedPermission`, constant `WILDCARD_PERMISSION`) are used identically across Tasks 1–4. `ScRoutingEntry.of(String, String, String, ModuleBootstrapEndpoint)` and `setPermissionsRequired(List<String>)` match the real signatures.
