# folio-module-sidecar

Quarkus sidecar deployed alongside every FOLIO module as a transparent HTTP proxy. Java, Quarkus, Vert.x, Kafka (SmallRye Reactive Messaging), Keycloak JWT (SmallRye JWT).
- **Ingress**: validate Keycloak JWT, enforce tenant authorization, forward to `MODULE_URL`.
- **Egress**: inject service/system-user token, do discovery lookup, forward module-to-module to the target sidecar.
- **Self-config**: fetch routing from `mgr-applications` via Module Descriptors.
- **Tenant lifecycle**: consume Kafka `discovery`, `entitlement`, `logout` topics.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn clean -Pfips install       # FIPS-compliant build
mvn test                       # unit tests (@UnitTest)
mvn verify                     # unit and integration tests (@IntegrationTest); needs Docker
mvn test -Dtest="TenantFilterTest#filter_positive"  # single test
mvn verify -Dit.test=CrossTenantIT -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # single IT, unit tests skipped
mvn verify -Pcoverage          # coverage report (JaCoCo 80% instruction rule only reports, does not fail the build)
mvn checkstyle:check           # process-classes phase; fails on warnings
```

Dev mode needs env: `SIDECAR_URL`, `MODULE_VERSION`, `MODULE_NAME`, `MODULE_URL`, `AM_CLIENT_URL`, `TE_CLIENT_URL`, `TM_CLIENT_URL` (see `README.md`). Checkstyle enforces **max method length 23 lines** in production code (suppressed in tests).

## Architecture

**Startup**: `SidecarInitializer` observes the Vert.x `Router`, then initializes routing (module bootstrap from `mgr-applications`) and tenant service (tenants + entitlements via Kafka/REST).

**Request flow**:
```
Client  -> IngressRequestHandler -> [filter chain] -> RequestForwardingService -> MODULE_URL
MODULE_URL -> EgressRequestHandler -> [filter chain] -> RequestForwardingService -> Target Sidecar
```

**Routing** (`service/routing/`): `RoutingConfiguration` builds a `ChainedHandler` pipeline — Ingress → Egress → optional Dynamic (`routing.dynamic.enabled`) → optional Gateway (`routing.forward-to-gateway.enabled`) → NotFound. Each `RoutingHandlerWithLookup` delegates to `.next()` on no match.

**Ingress filter order** (`IngressFilterOrder`): 90 RequestValidation · 100 SelfRequest · 110 KeycloakSystemJwt · 120 KeycloakJwt · 130 KeycloakTenant · 140 Tenant · 150 KeycloakImpersonation · 160 KeycloakAuthorization · 170 SidecarSignature · 171 DesiredPermissions. Read each filter's `shouldSkip` before changing the order.

**Stack**: Quarkus (CDI/Arc, REST, Reactive Messaging, Caffeine cache, Fault Tolerance), Vert.x WebClient (reactive proxying, `io.vertx.core.Future`), SmallRye JWT + `folio-auth-openid`, Kafka (SmallRye Reactive Messaging), Lombok.

**Config**: `src/main/resources/application.properties` with env-var substitution; test overrides in `src/test/resources/application.properties`. All env vars in `README.md`.

## Testing Conventions

- **Unit**: JUnit + Mockito, no Quarkus context; `@UnitTest`, `@ExtendWith(MockitoExtension.class)`.
- **Integration**: `@IntegrationTest` (includes `@QuarkusTest`); WireMock via `@EnableWireMock`; in-memory Kafka only where the test sets it up (`InMemoryMessagingExtension` or `InMemoryMessagingTestProfile`); other ITs start Kafka through Quarkus Dev Services, so integration tests need Docker.
- Naming: `methodName_positive/negative_description`.
- Never use lenient Mockito; verify only unmocked interactions; prefer helper methods over blanket `@BeforeEach` stubbing.
