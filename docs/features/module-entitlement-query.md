---
feature_id: module-entitlement-query
title: Module Entitlement Query Endpoint
status: active
updated: 2026-03-18
---

# Module Entitlement Query Endpoint

## What it does

The sidecar exposes `GET /entitlements/modules/{moduleId}` so the co-located module can query which tenants are currently entitled for it. The response is a JSON array of tenant name strings.

## Why it exists

FOLIO modules that consume Kafka events need to know which tenants are active to filter messages correctly. Without this endpoint, a module would have to maintain its own entitlement state independently. This endpoint delegates to the sidecar, which already tracks tenant entitlements from the entitlement Kafka topic and from `mgr-tenant-entitlements` at startup.

See [MODSIDECAR-180](https://folio-org.atlassian.net/browse/MODSIDECAR-180) and the [spike page on filtering Kafka messages by tenant entitlements](https://folio-org.atlassian.net/wiki/spaces/FOLIJET/pages/1699643426/Spike+-+Filter+Kafka+messages+by+tenant+entitlements) for background.

## Entry points

| Class                                    | Location                         | Role                                          |
|:-----------------------------------------|:---------------------------------|:----------------------------------------------|
| `ModuleEntitlementHandler`               | `service/routing/handler/`       | Handles the request and writes the response   |
| `RoutingConfiguration.ModuleEntitlement` | `service/routing/configuration/` | Wires the handler into the chain when enabled |
| `TenantService.getEnabledTenants()`      | `service/`                       | Provides the set of enabled tenants           |

## Request / response

**Request:**
```
GET /entitlements/modules/{moduleId}
```

No authentication headers are required. The handler is placed before the ingress filter chain and does not participate in JWT or tenant validation.

**Successful response:**
```
HTTP/1.1 200 OK
Content-Type: application/json

["tenant1", "tenant2"]
```

The array contains the names of all tenants for which the module is currently entitled. Order is not guaranteed. An empty array `[]` is returned when no tenants are enabled.

## Request flow

1. **`ChainedHandler` pipeline** receives the request before ingress/egress handlers
2. **`ModuleEntitlementHandler.handle()`** inspects path and method:
   - Path does not start with `/entitlements/modules/` → returns `false` (pass to next handler)
   - Method is not `GET` → returns `false` (pass to next handler, ultimately 404)
   - `{moduleId}` in path does not match this sidecar's module ID → returns failed future with `ForbiddenException`
3. **`TenantService.getEnabledTenants()`** is called, returning `Future<Set<String>>`
   - If tenant loading is still in progress, the future stays pending until loading completes
4. On success: responds `200 application/json` with a JSON array of tenant names

## Implementation

**`ModuleEntitlementHandler`** is a `ChainedHandler` that short-circuits on path and method before any service call. The module ID is injected at construction time from `ModuleProperties.getId()`.

**`TenantService.getEnabledTenants()`** maps the internal `loadingFuture` to an immutable copy of the in-memory `enabledTenants` set. Callers that arrive before initial loading is complete receive a pending future that resolves once loading finishes.

**Position in chain:** `ModuleEntitlementHandler` is inserted as the first handler in the `chainedHandler` pipeline — before `ingressHandler` — so it responds directly without passing through authentication filters.

**CDI wiring:** `RoutingConfiguration.ModuleEntitlement.moduleEntitlementHandler()` is annotated `@LookupIfProperty(name = "routing.module-entitlement.enabled", stringValue = "true")`. When the property is absent or false, the `Instance<ChainedHandler>` injected into `chainedHandler()` is not resolvable and the handler is omitted from the chain entirely.

## Configuration

| Environment Variable                 | Config Property                      | Default  | Description                                                       |
|:-------------------------------------|:-------------------------------------|:---------|:------------------------------------------------------------------|
| `ROUTING_MODULE_ENTITLEMENT_ENABLED` | `routing.module-entitlement.enabled` | `true`   | Enable the entitlement query endpoint. Set to `false` to disable. |

## Error handling

| Condition                                                     | HTTP Status  | Error type                    | Message                                                                    |
|:--------------------------------------------------------------|:-------------|:------------------------------|:---------------------------------------------------------------------------|
| `{moduleId}` in path does not match sidecar's own module ID   | 403          | `ForbiddenException`          | `Access Denied`                                                            |
| Method is not `GET` (e.g. POST)                               | 404          | `NotFoundException`           | `Route is not found [method: {method}, path: {path}]`                      |
| Tenant loading failed during `init()`                         | 500          | Propagated `RuntimeException` | Original exception message                                                 |
| Feature disabled (`routing.module-entitlement.enabled=false`) | 404          | `NotFoundException`           | `Route is not found [method: GET, path: /entitlements/modules/{moduleId}]` |

**403 note:** The `ErrorHandler` always maps `ForbiddenException` to `"Access Denied"` regardless of the exception's own message. The actual rejection reason (module ID mismatch) is logged at debug level.

## Logging

**Debug level:**
- `"Module entitlement handler added to the handlers chain"` — logged at startup when the handler is wired in

## Dependencies

| Dependency         | Purpose                                                        |
|:-------------------|:---------------------------------------------------------------|
| `TenantService`    | Provides `getEnabledTenants()` returning `Future<Set<String>>` |
| `ModuleProperties` | Supplies the sidecar's own module ID for path validation       |
| Vert.x `JsonArray` | Serialises the tenant set to a JSON array string               |

## Troubleshooting

| Symptom                                        | HTTP Status  | Root Cause                                                      | Fix                                                                                                |
|:-----------------------------------------------|:-------------|:----------------------------------------------------------------|:---------------------------------------------------------------------------------------------------|
| 404 for `GET /entitlements/modules/{moduleId}` | 404          | Feature disabled                                                | Set `ROUTING_MODULE_ENTITLEMENT_ENABLED=true`                                                      |
| 403 for correct module ID path                 | 403          | Path `{moduleId}` does not match `MODULE_ID` env var            | Verify `MODULE_ID` matches the path segment exactly                                                |
| Response hangs until timeout                   | N/A          | Tenant loading stuck (upstream services unreachable at startup) | Check connectivity to `mgr-tenant-entitlements` and `mgr-tenants`; review sidecar startup logs     |
| Empty array returned                           | 200          | No tenants are entitled yet                                     | Normal if no tenants have been installed; verify Kafka entitlement events are reaching the sidecar |
