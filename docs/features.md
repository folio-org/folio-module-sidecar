# FOLIO Module Sidecar - Feature Documentation

This directory contains detailed technical documentation for major features of the FOLIO Module Sidecar.

## Available Features

| Feature                                                       | Description                                                                                                           | Status    |
|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|-----------|
| [JWT Token Verification](features/jwt-token-verification.md)  | Asynchronous JWT token verification and parsing with RSA signature verification, JWKS caching, and claim extraction   | Active    |

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
