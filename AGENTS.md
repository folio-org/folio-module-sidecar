# folio-module-sidecar

Quarkus 3.x sidecar (Java 21) deployed alongside every FOLIO module as a transparent HTTP proxy:
- **Ingress**: validate Keycloak JWT, enforce tenant authorization, forward to `MODULE_URL`.
- **Egress**: inject service/system-user token, do discovery lookup, forward module-to-module to the target sidecar.
- **Self-config**: fetch routing from `mgr-applications` via Module Descriptors.
- **Tenant lifecycle**: consume Kafka `discovery`, `entitlement`, `logout` topics.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn clean -Pfips install       # FIPS-compliant build
mvn test -Dgroups=unit         # unit tests (@UnitTest)
mvn verify -Dgroups=integration                       # integration tests (@IntegrationTest)
mvn test -Dgroups=unit -Dtest="TenantFilterTest#filter_positive"  # single test
mvn verify -Pcoverage          # coverage (JaCoCo 80% instruction min)
mvn checkstyle:check           # process-classes phase; fails on warnings
```

Dev mode needs env: `SIDECAR_URL`, `MODULE_ID`, `MODULE_NAME`, `MODULE_URL`, `AM_CLIENT_URL`, `TE_CLIENT_URL`, `TM_CLIENT_URL` (see `README.md`). Checkstyle enforces **max method length 23 lines** in production code (suppressed in tests).

## Architecture

**Startup**: `SidecarInitializer` observes the Vert.x `Router`, then initializes routing (module bootstrap from `mgr-applications`) and tenant service (tenants + entitlements via Kafka/REST).

**Request flow**:
```
Client  -> IngressRequestHandler -> [filter chain] -> RequestForwardingService -> MODULE_URL
MODULE_URL -> EgressRequestHandler -> [filter chain] -> RequestForwardingService -> Target Sidecar
```

**Routing** (`service/routing/`): `RoutingConfiguration` builds a `ChainedHandler` pipeline — Ingress → Egress → optional Dynamic (`routing.dynamic.enabled`) → optional Gateway (`routing.forward-to-gateway.enabled`) → NotFound. Each `RoutingHandlerWithLookup` delegates to `.next()` on no match.

**Ingress filter chain** (`IngressFilterOrder`): 90 RequestValidation · 100 SelfRequest (bypass auth for sidecar-to-sidecar) · 110 KeycloakSystemJwt · 120 KeycloakJwt (parse Bearer) · 130 KeycloakTenant (token tenant == `x-okapi-tenant`) · 140 Tenant (enabled?) · 150 KeycloakImpersonation · 160 KeycloakAuthorization (RPT) · 170 SidecarSignature · 171 DesiredPermissions.

**Key packages**: `startup/` orchestration · `service/filter/` filters · `service/token/` egress token caching (`ServiceTokenProvider`, `SystemUserTokenProvider`) · `integration/am/` `ApplicationManagerClient` · `integration/te/` entitlement client + Kafka consumer · `integration/keycloak/` JWT/auth/impersonation/introspection (+ `filter/`) · `integration/cred/` `CredentialService` + `AsyncSecureStore` (Ephemeral/AWS-SSM/Vault/FSSP) · `integration/kafka/` consumers · `configuration/properties/` typed config · `model/` DTOs.

**Stack**: Quarkus 3.x (CDI/Arc, REST, Reactive Messaging, Caffeine cache, Fault Tolerance), Vert.x WebClient + Mutiny (reactive proxying), SmallRye JWT + `folio-auth-openid`, Kafka (SmallRye Reactive Messaging), Lombok.

**Config**: `src/main/resources/application.properties` with env-var substitution; test overrides in `src/test/resources/application.properties`. All env vars in `README.md`.

## Testing Conventions

- **Unit**: JUnit 5 + Mockito, no Quarkus context; `@UnitTest`, `@ExtendWith(MockitoExtension.class)`.
- **Integration**: `@IntegrationTest` (includes `@QuarkusTest`); WireMock via `@EnableWireMock`, in-memory Kafka via `InMemoryMessagingExtension`.
- Naming: `methodName_positive/negative_description`.
- Never use lenient Mockito; verify only unmocked interactions; prefer helper methods over blanket `@BeforeEach` stubbing.
- Support classes in `src/test/java/org/folio/sidecar/support/`; WireMock mappings in `src/test/resources/mappings/` (by service); JSON fixtures in `src/test/resources/json/`.
- Full unit-testing guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md
