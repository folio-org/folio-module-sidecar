# MODSIDECAR-198 — Implement wildcard permissionsRequired support

**Provider**: Jira · **Key**: MODSIDECAR-198 · **Type**: Story · **Priority**: P2
**URL**: https://folio-org.atlassian.net/browse/MODSIDECAR-198
**Status**: Ready for review (SDLC run 20260626-1014-MODSIDECAR-198)
**Branch**: MODSIDECAR-198 (4 code commits + 1 artifacts commit)

## Summary

Treat `permissionsRequired: ["*"]` as "valid token required, no named permission";
keep `permissionsRequired: []` as truly public. Only the exact singleton `["*"]`
counts as the wildcard convention.

## Outcome

- `RoutingUtils`: added `isTrulyPublic`, `isWildcardPermissionRequired`, `requiresNoNamedPermission`; removed the old `hasNoPermissionsRequired`.
- `KeycloakAuthorizationFilter`: skips Keycloak RPT for wildcard endpoints (the one behavioral change).
- `KeycloakTenantFilter` / `KeycloakJwtFilter`: switched to `isTrulyPublic` (explicit, behavior-preserving) so `["*"]` still requires a token and a validated tenant.
- All 6 acceptance criteria covered by tests. 541 unit + 73 integration tests pass; checkstyle clean. Code review approved (3 lenses, high confidence). Actual complexity: S (14/36).

## Linked Artifacts

- docs/superpowers/specs/2026-06-26-modsidecar-198-wildcard-permissions-design.md
- docs/superpowers/plans/2026-06-26-modsidecar-198-wildcard-permissions.md
- docs/superpowers/runs/20260626-1014-MODSIDECAR-198/requirements.md
- docs/superpowers/runs/20260626-1014-MODSIDECAR-198/qa-report.md
- docs/superpowers/runs/20260626-1014-MODSIDECAR-198/code-review-final.json

## History

- 2026-06-26T08:14:13Z — work_item.created (placeholder from raw_input)
- 2026-06-26T08:15:00Z — work_item.assigned — branch MODSIDECAR-198; requirements resolved from Jira
- 2026-06-26T09:40:00Z — work_item.transitioned — Ready for review; implementation, review, and QA complete
