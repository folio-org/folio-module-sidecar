# Design — MODSIDECAR-198: Wildcard `permissionsRequired` support

**Date**: 2026-06-26
**Ticket**: https://folio-org.atlassian.net/browse/MODSIDECAR-198 (Story, P2)
**Run**: 20260626-1014-MODSIDECAR-198
**Requirements**: `docs/superpowers/runs/20260626-1014-MODSIDECAR-198/requirements.md`

## Problem

The sidecar's ingress security filters key off a single predicate —
`RoutingUtils.hasNoPermissionsRequired(rc)` = `isEmpty(permissionsRequired)` — to
decide whether an endpoint is protected. This collapses two genuinely different
intents into one:

- **Truly public** (`permissionsRequired: []` or missing) — no token required
  (e.g. forgotten-password).
- **Authenticated, no named permission** — caller identity from a JWT is
  required, but no specific permission (e.g. `GET /users-keycloak/_self`).

Module descriptors need a way to express the second intent. The agreed
convention (from the MODSIDECAR-191 spike) is the exact singleton
`permissionsRequired: ["*"]`.

## Current behavior (verified in code)

For the three Keycloak ingress filters, with `permissionsRequired = ["*"]`
(a **non-empty** list, so `hasNoPermissionsRequired` returns `false` today):

| Filter (order) | Predicate today | Effect for `["*"]` today | Desired |
|---|---|---|---|
| `KeycloakJwtFilter` (120) | `hasNoPermissionsRequired` gates failure-tolerance | Token **required** (missing/invalid ⇒ 401) | ✅ already correct |
| `KeycloakTenantFilter` (130) | skips when `hasNoPermissionsRequired` | Tenant validation **runs** | ✅ already correct |
| `KeycloakAuthorizationFilter` (160) | skips when `hasNoPermissionsRequired` | RPT evaluation **runs** ❌ | must **skip** |

So the only behavioral gap is the authorization filter calling Keycloak RPT
evaluation for a wildcard endpoint, where there is no named permission to
evaluate. The JWT and tenant filters already behave correctly because `["*"]` is
non-empty — but they rely on the *implicit* emptiness check, which is fragile.

## Design decision — `RoutingUtils` helper API

The fix needs the filters to distinguish three states explicitly. Two options:

**Option A (chosen): rename + add, point each filter at the precise helper.**
- `isTrulyPublic(rc)` — replaces `hasNoPermissionsRequired`; `permissionsRequired` is null/empty.
- `isWildcardPermissionRequired(rc)` — `permissionsRequired` is the exact singleton `["*"]`.
- `requiresNoNamedPermission(rc)` — `isTrulyPublic(rc) || isWildcardPermissionRequired(rc)`.

Filters:
- `KeycloakJwtFilter` → `isTrulyPublic` (failure-tolerance only for truly-public; behavior unchanged, intent now explicit).
- `KeycloakTenantFilter` → `isTrulyPublic` (skip only for truly-public; wildcard keeps validating tenant; behavior unchanged).
- `KeycloakAuthorizationFilter` → `requiresNoNamedPermission` (**behavior change**: skip RPT for truly-public **and** wildcard).

**Option B (rejected): keep `hasNoPermissionsRequired`, only add helpers.**
Less churn, but leaves a misleadingly-named predicate (`hasNoPermissionsRequired`
is ambiguous once `["*"]` exists — does "no permissions" include wildcard?). For
security code, an ambiguous predicate invites future drift. The ticket also
explicitly calls for updating all three filters and `RoutingUtils`.

**Recommendation: Option A.** It is intention-revealing (Clean Code, FR3), makes
each filter's contract explicit, and prevents the emptiness check from silently
absorbing the wildcard case later. `hasNoPermissionsRequired` has exactly the
semantics of `isTrulyPublic`, and is internal (only the three filters call it),
so the rename is safe and confined.

## Wildcard predicate (narrow, deterministic — NFR2, AC6)

```java
public static final String WILDCARD_PERMISSION = "*";

public static boolean isWildcardPermissionRequired(RoutingContext rc) {
  var perms = getScRoutingEntry(rc).getRoutingEntry().getPermissionsRequired();
  return perms != null && perms.size() == 1 && WILDCARD_PERMISSION.equals(perms.get(0));
}
```

Only an exact singleton `["*"]` qualifies. `["*", "x"]` (size 2) is a
named-permission list and follows the normal path (AC6). `[]`/null is truly
public (AC1).

## Components & data flow

No new classes, no schema change, no new config. The change is localized to:

- `utils/RoutingUtils.java` — helper methods + `WILDCARD_PERMISSION` constant.
- `integration/keycloak/filter/KeycloakJwtFilter.java` — swap predicate.
- `integration/keycloak/filter/KeycloakTenantFilter.java` — swap predicate.
- `integration/keycloak/filter/KeycloakAuthorizationFilter.java` — swap predicate to `requiresNoNamedPermission`.

Request flow for `["*"]`: JWT(120) parses & requires token → Tenant(130)
validates tenant claim vs `x-okapi-tenant` → Authorization(160) **skips** → forward
to module. Missing token fails at JWT(120) with the existing
`UnauthorizedException` (NFR3: identical to other protected endpoints).

## Error handling

Reuses existing exceptions/paths — `UnauthorizedException` for missing/invalid
token (JWT filter) and tenant mismatch (tenant filter). No new error types.

## Testing strategy (FR10)

Unit tests only (matches the established per-filter unit-test pattern; no
Keycloak/Quarkus context needed):

- `RoutingUtilsTest`: `isTrulyPublic` (empty/null ⇒ true; non-empty ⇒ false);
  `isWildcardPermissionRequired` (`["*"]` ⇒ true; `["*","x"]` ⇒ false; `[]`/named ⇒
  false); `requiresNoNamedPermission` (truly-public ⇒ true, wildcard ⇒ true,
  named ⇒ false).
- `KeycloakAuthorizationFilterTest`: `shouldSkip` true for `["*"]`; false for
  `["*","x"]` and named (AC3, AC5, AC6).
- `KeycloakTenantFilterTest`: `shouldSkip` false for `["*"]` (validation runs);
  filter rejects tenant mismatch for `["*"]` (AC3, AC4).
- `KeycloakJwtFilterTest`: `["*"]` + missing token ⇒ rejected (AC2); `[]` +
  missing token ⇒ allowed/unchanged (AC1).

Existing tests for `[]` and named permissions must stay green (regression guard
for AC1/AC5).

## Out of scope

Downstream module descriptors, `applications-poc-tools`,
`mgr-tenant-entitlements`, and any descriptor schema changes.
