# FOLIO Module Sidecar - Feature Documentation

This directory contains detailed technical documentation for major features of the FOLIO Module Sidecar.

## Available Features

| Feature                                                       | Description                                                                                                           | Status    |
|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|-----------|
| [JWT Token Verification](features/jwt-token-verification.md)  | Asynchronous JWT token verification and parsing with RSA signature verification, JWKS caching, and claim extraction   | Active    |
| [Module Entitlement Query](features/module-entitlement-query.md) | Endpoint for querying which tenants are currently entitled for this module (`GET /entitlements/modules/{moduleId}`) | Active    |
| [Tenant-Scoped Egress Routing](features/tenant-scoped-egress-routing.md) | Resolves egress routes per tenant from each tenant's entitled applications so multi-version environments reach the correct provider version (flag-gated) | Active    |

---

## Quick Reference

### JWT Token Verification

**What it does:** Validates JWT tokens from Keycloak using async worker threads to prevent event loop blocking.

**Key capabilities:**
- RSA signature verification using JWKS public keys
- Async parsing on worker threads (prevents blocking)
- Supports user tokens, system tokens, and impersonation tokens
- Extracts claims: `user_id`, `sid` (session ID), origin tenant, expiration
- JWKS caching with configurable refresh intervals

**Entry points:**
- `KeycloakJwtFilter` - User token validation
- `KeycloakSystemJwtFilter` - System token validation
- `KeycloakImpersonationFilter` - Impersonated user token validation

**Configuration:**
- `KC_URL` - Keycloak base URL
- `KC_JWKS_REFRESH_INTERVAL` - JWKS cache refresh (default: 60s)
- `quarkus.vertx.worker-pool-size` - Worker thread pool size (default: 20)

---

### Module Entitlement Query

**What it does:** Exposes `GET /entitlements/modules/{moduleId}` so the co-located module can retrieve which tenants are currently entitled for it.

**Key capabilities:**
- Returns a JSON array of enabled tenant names
- Bypasses all authentication filters (positioned before ingress handler)
- Waits for tenant loading to complete before responding (async-safe)
- Validates that the path module ID matches the sidecar's own `MODULE_ID`
- Disabled cleanly when `ROUTING_MODULE_ENTITLEMENT_ENABLED=false`

**Entry points:**
- `ModuleEntitlementHandler` - Handles the request and writes the JSON response
- `TenantService.getEnabledTenants()` - Provides the current tenant set

**Configuration:**
- `ROUTING_MODULE_ENTITLEMENT_ENABLED` - Enable/disable the endpoint (default: `true`)
- `MODULE_ID` - The module ID this sidecar serves; used to validate the path parameter

---

### Tenant-Scoped Egress Routing

**What it does:** Resolves egress (module → required-module) routes per tenant, keyed by `X-Okapi-Tenant`, so a tenant reaches the provider version from its own entitled applications in multi-version environments. Off by default; egress routing is global when disabled.

**Key capabilities:**
- Per-tenant egress route tables built from each tenant's entitled applications (`GET /entitlements` → `POST /modules/{id}/bootstrap {applicationIds}`)
- Ingress loaded once at startup from `GET /modules/{id}/bootstrap`
- Tables reconciled on entitlement changes; revoked tenants dropped; `UPGRADE` and required-module discovery trigger rebuilds
- Unresolved egress falls through to the gateway (fallback defaults on when the feature is enabled)
- Build failures retain the previously built table and retry on the next event

**Entry points:**
- `EgressRoutingLookup` - Resolves the egress route table by `X-Okapi-Tenant`
- `EgressBootstrapService` - Builds/refreshes per-tenant tables; reconciles on `EntitlementsEvent`
- `RoutingService` - Ingress-only init; refreshes all tenants on required-module discovery
- `TenantEntitlementConsumer` - Refreshes a tenant's egress on `UPGRADE`

**Configuration:**
- `SIDECAR_TENANT_SCOPED_ROUTING_ENABLED` - Enable tenant-scoped egress routing (default: `false`)
- `SIDECAR_FORWARD_UNKNOWN_REQUESTS` - Gateway fallback for unresolved egress (defaults to the tenant-scoped flag's value)

---

## Documentation Standards

Each feature document follows this structure:
1. **Overview** - What the feature does and why it exists
2. **Technical deep-dive** - How it works under the hood
3. **Architecture** - System design and component interactions
4. **Configuration** - Environment variables and settings
5. **Security considerations** - Threat model and protections
6. **Troubleshooting** - Common issues and solutions

---

## Contributing

When documenting new features:
1. Create a new markdown file in `docs/features/`
2. Use kebab-case naming (e.g., `feature-name.md`)
3. Follow the structure of existing documents
4. Update this index file with a summary entry
5. Focus on **what** and **why**, not just **how**
