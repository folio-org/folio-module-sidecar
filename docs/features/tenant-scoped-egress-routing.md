---
feature_id: tenant-scoped-egress-routing
title: Tenant-Scoped Egress Routing
status: active
updated: 2026-06-18
---

# Tenant-Scoped Egress Routing

## What it does

When enabled, the sidecar resolves egress (module → required-module) routes **per tenant** instead of globally by module id. The target tenant is taken from the `X-Okapi-Tenant` header, and each tenant's egress routing table is built from the applications that tenant is entitled to. This ensures that, in environments where different tenants are entitled to different versions of the same application, an egress call reaches the provider version that belongs to the calling tenant.

The feature is gated by `routing.tenant-scoped.enabled` and is **off by default** — when disabled, egress routing keeps its existing global (module-id) behavior, unchanged.

## Why it exists

In a multi-version environment, several versions of the same application can be entitled to different tenants at the same time. The sidecar previously bootstrapped egress routing by module id only, so an egress request could resolve to the wrong provider version for a given tenant. Tenant-scoped routing makes egress resolution tenant-aware so each tenant reaches the correct provider version.

See [EUREKA-899](https://folio-org.atlassian.net/browse/EUREKA-899) (POC - Application-scoped sidecars bootstrap) for background.

## Entry point(s)

The feature is driven by tenant-entitlement signals that (re)build per-tenant egress tables, and consumed on the egress request path.

| Type | Event | Description |
|------|-------|-------------|
| Internal Event | `EntitlementsEvent` (`entitlements`) | Reconciles per-tenant egress tables when the enabled-tenant set changes — at startup and on every tenant enable/disable. Builds tables for newly enabled tenants and drops revoked tenants. |

| Type | Topic | Description |
|------|-------|-------------|
| Kafka Consumer | `${ENV}.entitlement` (channel `entitlement`) | On an `UPGRADE` event for this module, rebuilds the affected tenant's egress table (a version change that does not alter tenant membership). |

| Class | Location | Role |
|:------|:---------|:-----|
| `EgressRoutingLookup` | `service/routing/lookup/` | On the egress request path, resolves the route table by `X-Okapi-Tenant`; falls through (no match) when the tenant is missing/unknown or its table is not yet built. |
| `EgressBootstrapService` | `service/routing/` | Builds and refreshes each tenant's egress table from its entitled applications; reconciles on `EntitlementsEvent`. |
| `RoutingService` | `service/routing/` | Loads ingress-only at startup; on required-module discovery, refreshes all enabled tenants' egress tables. |
| `TenantEntitlementConsumer` | `integration/kafka/` | Triggers a tenant egress refresh on `UPGRADE`. |

### Event processing

- **When processed:** on each `EntitlementsEvent` (set reconcile) and on each `UPGRADE` Kafka entitlement message; required-module discovery refreshes all enabled tenants.
- **Processing behavior:** a tenant's table is built from `GET /entitlements?tenant={tenant}` → distinct application ids → `POST /modules/{moduleId}/bootstrap {applicationIds}`. A tenant with no entitled applications stores an empty table (no bootstrap call). A build failure leaves the previously built table intact and is retried on the next event.

## Business rules and constraints

- Disabled by default (`routing.tenant-scoped.enabled=false`); egress routing behavior is identical to before when off.
- When enabled, ingress routes are loaded once at startup from `GET /modules/{moduleId}/bootstrap`; egress is resolved from the per-tenant tables instead of a single global table.
- An egress request is matched against the table for its `X-Okapi-Tenant`. A missing or unknown tenant, or a tenant whose table has not been built yet, produces no match.
- Unmatched egress falls through the routing chain; with gateway fallback enabled it is forwarded to the gateway (see below) rather than returning 404.
- When tenant-scoped routing is enabled, `routing.forward-to-gateway.enabled` defaults to `true` so unresolved egress is forwarded to the gateway. An operator can still override it explicitly via `SIDECAR_FORWARD_UNKNOWN_REQUESTS`.
- Per-tenant tables are reconciled against the enabled-tenant set: newly enabled tenants are built, revoked tenants are dropped.

## Error behavior

- **Unresolved egress** (unknown/missing tenant, or table not yet built): no per-tenant match. With gateway fallback on (the default when tenant-scoped routing is enabled) the request is forwarded to the gateway; otherwise the chain returns `404 Route is not found`.
- **Bootstrap or entitlement fetch failure during a table build:** logged at warn level; the tenant's previously built table is retained and the build is retried on the next entitlement/discovery/upgrade event. No request-time error is introduced by a failed rebuild.

## Caching

The per-tenant egress routing table is held in memory as a `ConcurrentHashMap` keyed by tenant name, maintained by `EgressRoutingLookup`. Entries are added/replaced on a successful tenant build and removed when a tenant is revoked.

Operational notes for clustered deployments:
- Each sidecar instance maintains its own per-tenant tables, populated independently from the same upstream sources (`mgr-applications`, `mgr-tenant-entitlements`) and from the per-instance entitlement event stream.
- During the startup window, before a tenant's table is built, egress requests for that tenant fall through to the gateway (when fallback is enabled).

## Configuration

| Variable | Config property | Default | Purpose |
|----------|-----------------|---------|---------|
| `SIDECAR_TENANT_SCOPED_ROUTING_ENABLED` | `routing.tenant-scoped.enabled` | `false` | Enables tenant-scoped egress routing. |
| `SIDECAR_FORWARD_UNKNOWN_REQUESTS` | `routing.forward-to-gateway.enabled` | `false`, but defaults to the value of `SIDECAR_TENANT_SCOPED_ROUTING_ENABLED` when not set | Forwards unresolved egress to the gateway instead of returning 404. |
| `SIDECAR_FORWARD_UNKNOWN_REQUESTS_DESTINATION` | `routing.forward-to-gateway.destination` | `http://api-gateway:8000` | Gateway URL used for forwarded unresolved egress. |
| `AM_CLIENT_URL` | `am.url` | `http://mgr-applications:8081` | `mgr-applications` base URL — source of ingress and per-tenant egress bootstrap. |
| `TE_CLIENT_URL` | `te.url` | `http://mgr-tenant-entitlements:8081` | `mgr-tenant-entitlements` base URL — source of each tenant's entitled applications. |
| `ENV` | (topic prefix) | `folio` | Prefix of the consumed entitlement topic `${ENV}.entitlement`. |

## Dependencies and interactions

- **`mgr-applications`** — `GET /modules/{moduleId}/bootstrap` (ingress routes) and `POST /modules/{moduleId}/bootstrap` with body `{"applicationIds":[…]}` (per-tenant egress routes scoped to the tenant's entitled applications).
- **`mgr-tenant-entitlements`** — `GET /entitlements?tenant={tenant}` to resolve each tenant's entitled application ids.
- **Kafka** — consumes `${ENV}.entitlement`; an `UPGRADE` for this module triggers a per-tenant egress refresh.
- **`TenantService`** — its enabled-tenant cache is the source of truth for which tenants this module serves; changes are published as `EntitlementsEvent` and drive per-tenant egress reconcile.
