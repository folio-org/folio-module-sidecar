---
feature_id: tenant-scoped-egress-routing
title: Tenant-Scoped Egress Routing
status: active
updated: 2026-06-14
---

# Tenant-Scoped Egress Routing

## What it does

The sidecar maintains a separate egress (outbound, service-to-service) route table **per tenant**, scoped to the applications that tenant has installed. Each table is built by calling mgr-applications `POST /modules/{id}/bootstrap` with `type=egress` and the tenant's application IDs. When the module makes an outbound call, the request is matched against the calling tenant's table; if that tenant has no scoped table, the unmatched egress request is forwarded to the gateway.

The tables are kept current at runtime: they are loaded at startup for active tenants and refreshed in response to tenant entitlement changes (Kafka) and module discovery events.

## Why it exists

In a multi-tenant deployment different tenants can have different applications — and therefore different provider module versions — installed. A single global egress table cannot express which provider version a given tenant should reach. Scoping the egress table per tenant routes each tenant's outbound calls to the provider that is actually installed for that tenant. Falling back to the gateway for tenants without a scoped table preserves correctness during rollout and when the resolution is unavailable.

Background: EUREKA-899.

## Entry point(s)

| Type | Trigger | Description |
|------|---------|-------------|
| Startup | `SidecarInitializer.onStart` | After ingress routing and tenant init, loads scoped egress tables for all active tenants (best-effort) |
| Kafka Consumer | `${ENV:folio}.entitlement` topic (channel `entitlement`) | ENTITLE/UPGRADE enable a tenant; REVOKE/other disable it — each event refreshes that tenant's egress table |
| Internal Event | Module discovery (`RoutingService.onDiscovery`) | Refreshes egress tables only for tenants whose tracked provider set contains the discovered module |
| Egress request | `EgressRoutingLookup.lookupRoute` | Resolves an outbound request against the calling tenant's egress table |

### Event processing

- **Per-tenant serialization:** refreshes for the same tenant are tail-chained on non-blocking Vert.x futures, so the latest event's result is the one retained.
- **Discovery filtering:** a discovery event only refreshes tenants whose currently tracked egress scope includes the discovered module; other tenants are untouched.
- **Atomic swap:** a tenant's new route table is built fully before replacing the old one; other tenants' tables are never affected.

## Business rules and constraints

- **Per-tenant tables.** Egress routes are keyed by tenant; a request's tenant selects which table is consulted.
- **Active tenants only.** If no tenants are active at startup, the sidecar serves ingress only and builds no egress tables.
- **Application scope drives the table.** A tenant's scope is the sorted, distinct set of `applicationId`s from its entitlements (loaded with modules). The egress bootstrap is resolved against exactly that scope.
- **Unchanged scope is a no-op.** If a refresh computes the same application scope already tracked for the tenant, the egress table is not rebuilt.
- **Module-not-active removes the table.** If the sidecar's module is no longer entitled for a tenant, or the egress bootstrap returns `found=false` (module not in scope), that tenant's egress table is removed.
- **Discovery forces a reload.** A module discovery event for a tracked provider re-fetches the affected tenants' egress bootstrap even if the application scope is unchanged, so a changed provider location is picked up (the unchanged-scope no-op applies only to entitlement-driven refreshes).
- **Unmatched egress falls back to the gateway (default on).** If a tenant has no scoped table, or no entry matches, the unmatched **outbound** request is forwarded to the gateway rather than failing. This is controlled by `routing.egress.fallback-to-gateway.enabled` (default `true`) and reuses `routing.forward-to-gateway.destination`. Unmatched **inbound** (ingress) requests are unaffected — they still return 404. Setting the flag to `false` makes unmatched egress return 404 as well.

## Error behavior

- **Ingress bootstrap failure fails startup.** Loading the module's own (ingress) routes is required; failure shuts the sidecar down.
- **Missing endpoint is tolerated (both directions).** If mgr-applications returns 404/405 for `POST /modules/{id}/bootstrap` (older deployments without the endpoint): scoped egress is skipped (tenants forward egress to the gateway), and the ingress call falls back to the legacy `GET /modules/{id}`, so the sidecar still starts against an un-upgraded mgr-applications.
- **Scoped egress init is best-effort.** A failure while loading egress tables at startup is recovered and does **not** fail startup; affected tenants forward egress to the gateway until a later refresh succeeds.
- **Per-tenant refresh failures are isolated.** A failed refresh for one tenant is logged and does not affect other tenants; entitlement events are acknowledged immediately so a slow or failing refresh never blocks or halts the entitlement consumer. A disable (REVOKE) whose refresh fails still drops the tenant's egress table (fail-safe to the gateway).

## Caching

Each tenant's egress route table is held in memory (`EgressRoutingLookup`, a concurrent map keyed by tenant). Entries are replaced atomically on refresh and removed when the tenant is disabled or the module leaves the tenant's scope. The tables are rebuilt from upstream state at startup, so they are not durable across restarts.

## Configuration

| Variable | Purpose |
|----------|---------|
| `am.url` / `AM_CLIENT_URL` | Base URL of mgr-applications, used for the `POST /modules/{id}/bootstrap` ingress and egress calls (default `http://mgr-applications:8081`). |
| `te.url` / `TE_CLIENT_URL` | Base URL of mgr-tenant-entitlements, used to load each tenant's entitlements/application scope (default `http://mgr-tenant-entitlements:8081`). |
| `mp.messaging.incoming.entitlement.topic` | Entitlement Kafka topic that triggers per-tenant refresh (default `${ENV:folio}.entitlement`). |
| `routing.egress.fallback-to-gateway.enabled` / `SIDECAR_EGRESS_FALLBACK_TO_GATEWAY` | Forward unmatched outbound (egress) requests to the gateway when a tenant has no scoped table (default `true`). Inbound requests are unaffected. |

## Dependencies and interactions

- **Depends on: mgr-applications** — `POST /modules/{id}/bootstrap` with `type=egress` (per-tenant application scope) and `type=ingress` (the module's own routes), falling back to the legacy `GET /modules/{id}` for ingress against an un-upgraded mgr-applications. See the mgr-applications `module-bootstrap` feature.
- **Depends on: mgr-tenant-entitlements** — `GET /entitlements` (paginated across all pages, `includeModules=true`) to determine whether the module is active for a tenant and to compute the tenant's application scope.
- **Kafka** — consumes the `${ENV:folio}.entitlement` topic to react to tenant entitlement changes; module discovery events drive scope-aware refreshes.
