---
feature_id: jwt-token-verification
title: JWT Token Verification and Parsing
status: active
updated: 2026-01-20
---

# JWT Token Verification and Parsing

## What it does

The sidecar validates JWT tokens from Keycloak by verifying RSA signatures against JWKS public keys and extracting claims. Parsing runs asynchronously on worker threads to prevent event loop blocking.

## Why it exists

JWT signature verification involves CPU-intensive RSA cryptography. Running this on the Vert.x event loop would block request processing. The async implementation offloads verification to worker threads, maintaining high throughput.

## Entry points

| Filter | Header | Purpose |
|--------|--------|---------|
| `KeycloakJwtFilter` | `X-Okapi-Token` or `Authorization` | Parse user tokens |
| `KeycloakSystemJwtFilter` | `X-System-Token` | Parse system/service tokens |
| `KeycloakImpersonationFilter` | (from impersonation response) | Parse impersonated user tokens |

## Verification flow

1. **Extract token** from request headers (`X-Okapi-Token`, `Authorization`, or `X-System-Token`)
2. **Submit to worker thread** via `AsyncJsonWebTokenParser.parseAsync()`
3. **Fetch JWKS** from Keycloak (cached, 60min TTL)
4. **Verify signature** using RSA public key matching token's `kid` header
5. **Validate claims** expiration (`exp`) and issuer (`iss`)
6. **Extract claims** into `JsonWebToken` object
7. **Store in context** for downstream filters

## Implementation

**AsyncJsonWebTokenParser** wraps the synchronous `JsonWebTokenParser` from the `folio-jwt-openid` library. Uses `vertx.executeBlocking(callable, false)` where `false` enables unordered parallel execution across worker threads. All exceptions are wrapped in `UnauthorizedException`.

**Parser configuration** uses `JwtParserConfiguration` with issuer root URI from Keycloak URL and optional URI validation. JWKS provider is configured with refresh intervals and optional base URL override.

## Claims extracted and used

| Claim | Type | Usage |
|-------|------|-------|
| `iss` | Standard | Extract tenant from issuer URL: last segment after final `/` |
| `exp` | Standard | Token expiration timestamp for cache TTL and validation |
| `user_id` | Custom | FOLIO user ID for authorization cache key, audit logs, populate `X-Okapi-User-Id` header |
| `sid` | Custom | Session ID for authorization cache key and logout invalidation |
| (raw token) | - | Full JWT string passed to Keycloak for permission evaluation |

**Extraction utilities** in `JwtUtils` provide methods to get origin tenant from issuer, extract custom claims (`user_id`, `sid`), and dump all claims for debugging (token values are hashed).

## Context storage

Parsed tokens are stored in `RoutingContext` for downstream filter access:

- **User token**: Context key `"parsedToken"`
- **System token**: Context key `"X-System-Token"` (reuses header name)
- **Origin tenant**: Context key `"originTenant"` (extracted from issuer)

## Authorization cache integration

**Cache key format**: `{path}#{method}#{tenant}#{user_id}#{session_id}#{expiration}`

Example: `/users#GET#diku#admin#1f345678-9abc#1705942800`

**Cache key construction**: Permission (static path + HTTP method), tenant, conditionally include `user_id` and `sid` if present in token, and expiration time.

**Cache invalidation**: On logout events, entries are matched by session ID (for logout) or user ID (for user deletion). Matching entries are removed from authorization cache.

## Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `KC_URL` | `http://keycloak:8080` | Keycloak base URL for issuer validation (`keycloak.url`) |
| `KC_URI_VALIDATION_ENABLED` | `true` | Validate token `iss` claim matches Keycloak URL (`keycloak.uri-validation.enabled`) |
| `KC_JWKS_REFRESH_INTERVAL` | `60` | JWKS cache refresh interval in minutes (`keycloak.jwt-cache.jwks-refresh-interval`) |
| `KC_FORCED_JWKS_REFRESH_INTERVAL` | `60` | Force JWKS refresh interval in minutes - used during signing key rotation (`keycloak.jwt-cache.forced-jwks-refresh-interval`) |
| `KC_JWKS_BASE_URL` | ` ` (empty) | Override JWKS endpoint URL - if not set, extracted from token's `iss` claim (`keycloak.jwks-base-url`) |

**Vert.x worker pool (Quarkus defaults):**
- `quarkus.vertx.worker-pool-size`: `20` threads
- `quarkus.vertx.max-worker-execute-time`: `60s`

## Error handling

All parsing errors are converted to `UnauthorizedException`:

| Error Type | Cause |
|------------|-------|
| `ParseException` | Invalid JWT structure, Base64 encoding, JSON, or signature verification failure |
| Token expired | `exp` claim is in the past |
| Invalid issuer | `iss` claim doesn't match Keycloak URL (when validation enabled) |
| `RuntimeException` | Unexpected errors during parsing |
| Async execution failure | Worker thread pool exhaustion or timeouts |

### Special case: KeycloakJwtFilter bypass logic

Request proceeds **even if token parsing fails** under these conditions:

1. **No permissions required** AND error is NOT a JWT parsing failure
2. **Self-request** (internal module call) AND no token was provided
3. **Valid system token present** (system token takes precedence)

### Special case: Invalid JWT segments with system token

When a JWT has invalid segments but a system token is present, the request is allowed. This handles "dummy" tokens from `mod-pubsub-client` during tenant install.

## Logging

**Debug level:**
- "Parsing JWT token on worker thread"
- "Caching access token: key = {cacheKey}"
- "Invalidating authZ cached token: key = {key}"

**Warning level:**
- "Failed to parse JWT token: {message}" (ParseException)
- "Failed to parse JWT token due to unexpected error" (RuntimeException)
- "Failed to parse JWT token due to async execution failure" (async errors)
- "System token not found in the request headers, cannot process system JWT"

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `folio-jwt-openid` | Provides `JsonWebTokenParser` and `OpenidJwtParserProvider` |
| Eclipse MicroProfile JWT | `JsonWebToken` interface |
| Vert.x | Worker thread pool via `vertx.executeBlocking()` |
| Caffeine | Authorization token caching with TTL |
| Quarkus Security | `UnauthorizedException` exception type |

## JWKS endpoint resolution

The parser fetches public keys from the JWKS endpoint. The URL is determined by:

1. **Extract from token issuer** (default): Parse the `iss` claim from the JWT and derive JWKS URL
   - Token `iss`: `http://keycloak:8080/realms/diku`
   - JWKS URL: `http://keycloak:8080/realms/diku/protocol/openid-connect/certs`

2. **Override with environment variable** (optional): Set `KC_JWKS_BASE_URL` to use a different JWKS endpoint
   - Useful when issuer URL doesn't match actual Keycloak location
   - Example: `KC_JWKS_BASE_URL=https://auth.example.com`

**JWKS response format:**
```json
{
  "keys": [
    {
      "kid": "qJr6ysS_hauNBc65Sp16ORFOqJtII3ej6uAP2-jOnuo",
      "kty": "RSA",
      "alg": "RS256",
      "use": "sig",
      "n": "xGOr-H7A...",
      "e": "AQAB"
    }
  ]
}
```

**Key fields:**
- `kid`: Key identifier matching JWT header
- `kty`: Key type (RSA)
- `n`, `e`: RSA public key components (modulus and exponent)

## Troubleshooting

| Symptom | Root Cause | Fix |
|---------|------------|-----|
| "Failed to parse JWT" | Token missing or invalid structure (not 3 segments) | Verify client sends valid JWT with header.payload.signature format |
| "JWT signature verification failed" | Token signed with wrong key or tampered | Check `KC_URL` points to correct Keycloak, verify `kid` in token header matches JWKS |
| "Token expired" | `exp` claim is past current time | Check clock sync (NTP), verify token TTL settings in Keycloak |
| "System token not found" | Missing `X-System-Token` header | Configure service client credentials, ensure calling service populates header |
| "Failed to process system JWT" | System token blank or malformed | Verify system token generation and transmission |
| High parse latency | Worker pool exhausted or CPU-bound parsing | Increase worker pool size via Quarkus config, scale horizontally |
| JWKS fetch failures | Keycloak unreachable or wrong URL | Verify `KC_URL` and `KC_JWKS_BASE_URL`, check network connectivity |

## Performance characteristics

- JWT parsing involves RSA signature verification (CPU-intensive)
- Async execution prevents event loop blocking
- Worker pool size (default 20) limits concurrent parsing operations
- JWKS caching (60min) reduces network calls to Keycloak
- Authorization cache reduces repeated permission evaluations
