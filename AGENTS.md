## Project Overview

`folio-module-sidecar` is a Quarkus-based sidecar application that provides ingress and egress routing for FOLIO modules. It acts as a proxy, handling authentication/authorization via Keycloak, routing module-to-module communication, and self-configuration using Okapi Module Descriptors.

**Key Technologies:**
- Quarkus 3.29.2 (Java 21)
- Vert.x for reactive request handling
- Kafka for event-driven updates
- Keycloak for authentication/authorization
- Native compilation support with GraalVM

## Common Commands

### Building and Testing

```bash
# Standard build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run unit tests only (tagged with @Tag("unit"))
mvn test

# Run integration tests (tagged with @Tag("integration"))
mvn verify

# Run tests with coverage report
mvn clean verify -Pcoverage

# Checkstyle validation (runs automatically during build)
mvn checkstyle:check
```

### Running in Development Mode

```bash
# Run with live reload (example for mod-users-18.2.0)
mvn clean quarkus:dev \
  -Dquarkus.http.port=19002 \
  -DSIDECAR_URL="http://localhost:19002" \
  -DMODULE_ID="mod-users-18.2.0" \
  -DMODULE_NAME="mod-users" \
  -DMODULE_URL="http://localhost:9002" \
  -DAM_CLIENT_URL="http://mgr-applications:8081" \
  -DTE_CLIENT_URL="http://mgr-tenant-entitlements:8081" \
  -DTM_CLIENT_URL="http://mgr-tenants:8081" \
  -Ddebug=11002
```

Remote debugger can be attached on port 11002. Quarkus Dev UI is available at http://localhost:19002/q/dev/

### Running a Single Test

```bash
# Run a specific test class
mvn test -Dtest=ClassName

# Run a specific test method
mvn test -Dtest=ClassName#methodName
```

### Native Image Building

```bash
# Native build with GraalVM installed locally
mvn package -Pnative

# Portable native build (for different CPU architectures)
mvn package -Pnative -Dnative.march=-march=compatibility

# Native build in container (no GraalVM required)
mvn install -Pnative -DskipTests \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21
```

### Docker Image Building

```bash
# JVM-based Docker image
mvn clean install -DskipTests
docker build -t folio-module-sidecar .

# Native Docker image (build native executable first)
docker build -f docker/Dockerfile.native-micro -t folio-module-sidecar-native .

# FIPS-compliant image
export QUARKUS_SECURITY_SECURITY_PROVIDERS=BCFIPSJSSE
mvn clean -Pfips install
docker build -f docker/Dockerfile.fips -t {{image-tag}}:{{image-version}}
```

## Architecture Overview

### Request Flow

The sidecar operates in two modes:

1. **Ingress (incoming requests to the module):**
  - Client → Sidecar → Apply IngressRequestFilters → Underlying Module
  - Filters handle: JWT validation, Keycloak authorization, tenant validation, signature verification
  - Path processing strips/adds module path prefixes based on configuration

2. **Egress (outgoing module-to-module requests):**
  - Module → Sidecar → Apply EgressRequestFilters → Target Module's Sidecar
  - Filters handle: Token population (service tokens, system user tokens), sidecar signature injection
  - Dynamic routing lookup determines target module location

### Core Components

**RoutingService** (`service/routing/RoutingService.java`)
- Central orchestrator for request routing
- Loads ModuleBootstrap from mgr-applications on startup
- Registers all request handlers in a chain
- Listens to Kafka discovery events to update routes dynamically

**Request Handlers** (`service/routing/handler/`)
- `IngressRequestHandler`: Processes incoming requests, applies ingress filters, forwards to underlying module
- `EgressRequestHandler`: Processes outgoing requests, populates system tokens, forwards to target module
- Handlers are chained via `ChainedHandler` and execute in order

**Request Filters** (`service/filter/`)
- **IngressRequestFilter interface**: Filters applied to incoming requests (ordered execution)
  - `KeycloakJwtFilter`: Validates JWT signature and claims
  - `KeycloakAuthorizationFilter`: Performs UMA authorization via Keycloak
  - `KeycloakTenantFilter`: Validates tenant in token matches x-okapi-tenant header
  - `SidecarSignatureFilter`: Verifies inter-sidecar request signatures
  - `RequestValidationFilter`: Validates required headers
  - `DesiredPermissionsFilter`: Populates module permissions
- **EgressRequestFilter interface**: Filters applied to outgoing requests
  - Token providers inject service account tokens

**Routing Lookup** (`service/routing/lookup/`)
- `IngressRoutingLookup`: Determines if request is for the underlying module
- `EgressRoutingLookup`: Finds target module location from discovery cache
- `DynamicRoutingLookup`: Uses Kafka-based discovery events to maintain module location cache
- `GatewayRoutingLookup`: Falls back to API gateway for unknown modules (if configured)

### Integration Points

**Keycloak Integration** (`integration/keycloak/`)
- `KeycloakService`: Obtains tokens (user, service account, refresh)
- `KeycloakImpersonationService`: Handles user impersonation scenarios
- `IntrospectionService`: Token introspection for cross-tenant requests
- JWT parsing uses cached JWKS from Keycloak realm

**Manager Services Integration**
- `ApplicationManagerService` (`integration/am/`): Fetches ModuleBootstrap (module descriptors, permissions, routing)
- `TenantEntitlementService` (`integration/te/`): Retrieves tenant entitlements
- `TenantService` (`integration/tm/`): Batch-loads tenant information

**Kafka Integration** (`integration/kafka/`)
- `DiscoveryPublisher`: Publishes discovery events when routes change
- `DiscoveryListener`: Listens to `{{env}}.discovery` topic for module location updates
- `EntitlementEventListener`: Listens to `{{env}}.entitlement` topic for tenant entitlement changes
- `LogoutEventListener`: Listens to `{{env}}.{{tenant}}.mod-login-keycloak.logout` for logout events

**Secure Storage** (`integration/cred/`)
- `CredentialService`: Retrieves client credentials from secure storage
- Supports: EPHEMERAL, AWS_SSM, VAULT, FSSP
- Credentials format: `/{{ENV}}/{{TENANT}}/{{MODULE_NAME}}/{{KC_CLIENT_ID}}`

### Key Patterns

**Filter Chain Pattern**: IngressRequestFilters and EgressRequestFilters use ordered execution (via `Ordered` interface). Each filter returns `Future<RoutingContext>` allowing async, non-blocking processing.

**Module Bootstrap**: On startup, sidecar loads ModuleBootstrap containing:
- Primary module descriptor (the module this sidecar fronts)
- Required modules (dependencies for egress routing)
- Module permissions for authorization

**Token Caching**: Service tokens and authorization decisions are cached with TTL to reduce Keycloak load:
- `ServiceTokenProvider`: Caches service account tokens
- `SystemUserTokenProvider`: Caches system user tokens
- Keycloak authorization cache: Stores RPT (Requesting Party Token) results

**Dynamic Routing**: Module locations are discovered via Kafka events, allowing runtime route updates without restart.

**Sidecar Signature**: Inter-sidecar requests include X-Sidecar-Signature header for verification, ensuring requests come from trusted sidecars.

## Project Structure

```
src/main/java/org/folio/sidecar/
├── configuration/           # Quarkus configuration, properties
├── integration/            # External service integrations
│   ├── am/                # mgr-applications client
│   ├── te/                # mgr-tenant-entitlements client
│   ├── tm/                # mgr-tenants client
│   ├── keycloak/          # Keycloak client, filters
│   ├── kafka/             # Kafka event listeners/publishers
│   ├── users/             # mod-users-keycloak integration
│   └── cred/              # Secure credential storage
├── service/
│   ├── routing/           # Core routing logic, handlers, lookups
│   ├── filter/            # Request filters (ingress/egress)
│   ├── token/             # Token providers and caching
│   └── auth/              # JWT parsing
├── model/                 # Domain models
├── health/                # Health check endpoints
└── utils/                 # Utility classes
```

## Testing

Tests are categorized with JUnit 5 tags:
- `@Tag("unit")`: Unit tests (run with `mvn test`)
- `@Tag("integration")`: Integration tests (run with `mvn verify`)

Test support classes: `src/test/java/org/folio/support/types/`

WireMock is used extensively for mocking external HTTP services (Keycloak, manager services).

## Lombok Configuration

`lombok.config` in project root configures Lombok annotation processing. Lombok is used for `@Log4j2`, `@RequiredArgsConstructor`, `@Data`, etc.

## Code Quality

- **Checkstyle**: Enforced via `folio-java-checkstyle` rules (see `checkstyle/` directory)
- **Jacoco Coverage**: Minimum 80% instruction coverage enforced with `-Pcoverage` profile
- **Sonar Exclusions**: Model classes and some generated code excluded from analysis (see pom.xml)

## Environment Variables

Critical environment variables (see README.md for complete list):
- `MODULE_NAME`, `MODULE_URL`: Underlying module identification
- `SIDECAR_URL`: Self URL for module-to-module communication
- `KC_URL`: Keycloak URL
- `KC_SERVICE_CLIENT_ID`: Tenant-specific client for egress authentication
- `SECRET_STORE_TYPE`: Type of secure storage (EPHEMERAL, AWS_SSM, VAULT, FSSP)
- `AM_CLIENT_URL`, `TE_CLIENT_URL`, `TM_CLIENT_URL`: Manager service URLs
- `ROUTING_DYNAMIC_ENABLED`: Enables Kafka-based dynamic routing

## Important Implementation Notes

- **Path Processing**: `PathProcessor` handles module path prefix stripping/addition based on `SIDECAR_MODULE_PATH_PREFIX_ENABLED` and `SIDECAR_MODULE_PATH_PREFIX_STRATEGY`
- **Retry Logic**: `RetryTemplate` and Quarkus Fault Tolerance (`@Retry`) used for resilient external calls
- **Transaction Logging**: `TransactionLogHandler` logs access logs in Common Log Format with timing metrics (rt, uct, uht, urt)
- **Cross-Tenant Requests**: Enabled via `ALLOW_CROSS_TENANT_REQUESTS`, requires token introspection
- **FIPS Mode**: Supported via `-Pfips` profile, uses BouncyCastle FIPS crypto provider

## Pull Request Guidelines

When creating PRs, update:
- `NEWS.md`: Add entry with issue key and change description
- `README.md`: Update if new environment variables or configuration added
- Self-review code before submission
- Ensure all tests pass locally
