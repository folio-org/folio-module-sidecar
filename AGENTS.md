# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Overview

This is a **Quarkus 3.x** sidecar application for the FOLIO library services platform. It acts as a transparent HTTP proxy alongside every FOLIO module, handling cross-cutting concerns:

- **Ingress**: Validates JWT tokens (Keycloak), enforces tenant authorization, then forwards to `MODULE_URL`
- **Egress**: Injects service/system-user tokens, performs discovery lookups, then forwards module-to-module calls to target sidecar
- **Self-configuration**: Fetches routing entries from `mgr-applications` via Module Descriptors
- **Tenant lifecycle**: Subscribes to Kafka topics (`discovery`, `entitlement`, `logout`) to track active tenants

## Build Commands

```shell
# Full build with tests
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Dev mode with live reload (minimum required env vars)
mvn clean quarkus:dev \
  -Dquarkus.http.port=19002 \
  -DSIDECAR_URL="http://localhost:19002" \
  -DMODULE_ID="mod-users-18.2.0" \
  -DMODULE_NAME="mod-users" \
  -DMODULE_URL="http://localhost:9002" \
  -DAM_CLIENT_URL="http://mgr-applications:8081" \
  -DTE_CLIENT_URL="http://mgr-tenant-entitlements:8081" \
  -DTM_CLIENT_URL="http://mgr-tenants:8081"

# FIPS-compliant build
mvn clean -Pfips install

# Checkstyle (runs automatically at process-classes phase)
mvn checkstyle:check
```

## Test Commands

Tests are tagged with `@UnitTest` (`@Tag("unit")`) or `@IntegrationTest` (`@Tag("integration")`). Surefire runs unit tests; Failsafe runs integration tests.

```shell
# All tests
mvn test

# Unit tests only
mvn test -Dgroups=unit

# Integration tests only
mvn verify -Dgroups=integration

# Single unit test class
mvn test -Dgroups=unit -Dtest=TenantFilterTest

# Single unit test method
mvn test -Dgroups=unit -Dtest="TenantFilterTest#filter_positive"

# Single integration test class
mvn verify -Dgroups=integration -Dit.test=SidecarIT

# With coverage report (80% instruction coverage required)
mvn verify -Pcoverage
```

## Code Quality Rules

- **Checkstyle**: Uses `folio-java-checkstyle` ruleset. Fails on warnings. Runs at `process-classes` phase on both main and test sources.
- **Max method length: 23 lines** in production code (enforced by checkstyle). This is suppressed for test files.
- **JaCoCo**: 80% instruction coverage minimum at bundle level (via `-Pcoverage` profile).

## Architecture

### Startup Sequence

`SidecarInitializer` observes the Vert.x `Router` event, then:
1. Initializes routing (fetches module bootstrap from `mgr-applications`)
2. Initializes tenant service (loads tenants + entitlements from Kafka/REST)

### Request Flow

```
Client -> IngressRequestHandler -> [filter chain] -> RequestForwardingService -> MODULE_URL
MODULE_URL -> EgressRequestHandler -> [filter chain] -> RequestForwardingService -> Target Sidecar
```

### Routing Chain

`RoutingConfiguration` builds a `ChainedHandler` pipeline:

```
ScRequestHandler
  -> IngressRoutingLookup + IngressRequestHandler
  -> EgressRoutingLookup + EgressRequestHandler
  -> [optional: DynamicRoutingLookup]   // if routing.dynamic.enabled=true
  -> [optional: GatewayRoutingLookup]   // if routing.forward-to-gateway.enabled=true
  -> NotFoundHandler
```

Each `RoutingHandlerWithLookup` tries its lookup; if no match, delegates to `.next()`.

### Ingress Filter Chain (ordered by `IngressFilterOrder`)

| Order | Filter | Purpose |
|-------|--------|---------|
| 90 | `RequestValidationFilter` | Basic request validation |
| 100 | `SelfRequestFilter` | Bypass auth for sidecar-to-sidecar calls |
| 110 | `KeycloakSystemJwtFilter` | System JWT validation |
| 120 | `KeycloakJwtFilter` | Parse and validate Bearer token |
| 130 | `KeycloakTenantFilter` | Tenant from token matches `x-okapi-tenant` |
| 140 | `TenantFilter` | Check tenant is enabled |
| 150 | `KeycloakImpersonationFilter` | Impersonation token handling |
| 160 | `KeycloakAuthorizationFilter` | RPT token authorization |
| 170 | `SidecarSignatureFilter` | Sidecar signature validation |
| 171 | `DesiredPermissionsFilter` | Desired permissions extraction |

### Key Packages

| Package | Responsibility |
|---|---|
| `startup/` | `SidecarInitializer` -- startup orchestration |
| `service/routing/` | Handler chain, routing lookup, `RoutingConfiguration` (wires the chain) |
| `service/filter/` | Filter pipeline implementations |
| `service/token/` | `ServiceTokenProvider`, `SystemUserTokenProvider` -- token caching for egress |
| `integration/am/` | `ApplicationManagerClient` -- fetches module bootstrap from `mgr-applications` |
| `integration/te/` | `TenantEntitlementClient` + Kafka consumer for `entitlement` topic |
| `integration/keycloak/` | JWT parsing, Keycloak auth client, impersonation, token introspection |
| `integration/keycloak/filter/` | Keycloak-specific ingress filters |
| `integration/cred/` | `CredentialService` -- credential caching; `AsyncSecureStore` abstraction (Ephemeral/AWS SSM/Vault/FSSP) |
| `integration/kafka/` | Kafka consumers for `discovery`, `entitlement`, `logout` topics |
| `configuration/properties/` | Typed config beans: `ModuleProperties`, `SidecarProperties`, etc. |
| `model/` | DTOs: `ScRoutingEntry`, `EntitlementsEvent`, `ResultList`, `ModulePrefixStrategy` |

### Technology Stack

- **Java 21**, **Quarkus 3.x** (CDI/Arc, REST, Reactive Messaging, Cache/Caffeine, Fault Tolerance)
- **Vert.x WebClient + Mutiny** for reactive HTTP proxying
- **SmallRye JWT + `folio-auth-openid`** for token validation
- **Kafka** (SmallRye Reactive Messaging) for tenant lifecycle events
- **Lombok** for boilerplate reduction

### Configuration

Runtime configuration is in `src/main/resources/application.properties` with env-var substitution. Test overrides are in `src/test/resources/application.properties` (ephemeral secret store, in-memory Kafka, random port). All supported environment variables are documented in `README.md`.

## Testing Conventions

- **Unit tests**: JUnit 5 + Mockito (no Quarkus context). Annotated `@UnitTest`, `@ExtendWith(MockitoExtension.class)`.
- **Integration tests**: `@IntegrationTest` (includes `@QuarkusTest`). Use WireMock via `@EnableWireMock` + in-memory Kafka via `InMemoryMessagingExtension`.
- **Test naming**: `methodName_positive/negative_description` (e.g., `filter_positive`, `filter_negative_unknownTenant`)
- **Never use `@MockitoSettings(strictness = Strictness.LENIENT)`** -- write precise stubs instead.
- **Only verify unmocked interactions** -- verifying methods already stubbed with `when()` is redundant.
- **Use helper methods** for common mock setups instead of `@BeforeEach` that stubs everything.
- **Test support classes** are in `src/test/java/org/folio/sidecar/support/`: `TestConstants`, `TestValues`, `TestJwtGenerator`, `TestUtils`.
- **WireMock mappings** live in `src/test/resources/mappings/` organized by service (am/, keycloak/, te/, tm/, users/).
- **JSON fixtures** live in `src/test/resources/json/`.
- Detailed unit testing guidelines (Mockito patterns, parameterized tests, verification patterns) are at https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md
