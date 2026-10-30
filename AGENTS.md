# folio-module-sidecar

Quarkus sidecar (Java 21) deployed alongside every FOLIO module as a transparent HTTP proxy:
- Ingress: validate Keycloak JWT, enforce tenant authorization, forward to `MODULE_URL`.
- Egress: inject service/system-user token, do discovery lookup, forward module-to-module to the target sidecar.
- Self-config: fetch routing from `mgr-applications` via Module Descriptors.
- Tenant lifecycle: consume Kafka `discovery`, `entitlement`, `logout` topics.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn clean -Pfips install       # FIPS-compliant build
mvn test -Dgroups=unit         # unit tests (@UnitTest)
mvn verify -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # integration tests only (@IntegrationTest)
mvn test -Dgroups=unit -Dtest="TenantFilterTest#filter_positive"  # single unit test
mvn verify -Dit.test=SidecarIT -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # single IT, skips unit tests
mvn verify -Pcoverage          # coverage report (JaCoCo 80% instruction threshold; reported only, the build does not fail)
mvn checkstyle:check           # process-classes phase; fails on warnings
```

Dev mode needs env: `SIDECAR_URL`, `MODULE_NAME`, `MODULE_VERSION`, `MODULE_URL`, `AM_CLIENT_URL`, `TE_CLIENT_URL`, `TM_CLIENT_URL` (see `README.md`). Checkstyle enforces max method length 23 lines in production code (suppressed in tests).

## Style

Match the repository's existing style: use maintained implementations and tests as templates for new code, and keep the structure, naming, and conventions of neighboring files. Mockito is never lenient; verify only unmocked interactions; name tests `methodName_positive/negative_description`. Full unit-testing guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md

## Architecture notes

- Startup: `SidecarInitializer` observes the Vert.x `Router`, then initializes routing (module bootstrap from `mgr-applications`) and tenant service (tenants + entitlements via Kafka/REST). With `routing.tenant-scoped.enabled` (off by default), startup loads only the ingress routes and `EgressBootstrapService` builds the egress routes per tenant.
- Routing (`service/routing/`): `RoutingConfiguration` builds a `ChainedHandler` pipeline: optional ModuleEntitlement (`routing.module-entitlement.enabled`, on by default; answers `GET /entitlements/modules/{moduleId}` without the ingress filter chain) → Ingress → Egress → optional Dynamic (`routing.dynamic.enabled`) → optional Gateway (`routing.forward-to-gateway.enabled`) → NotFound. Each `RoutingHandlerWithLookup` delegates to `.next()` on no match.
- Ingress filter chain (`IngressFilterOrder`): 90 RequestValidation · 100 SelfRequest · 110 KeycloakSystemJwt · 120 KeycloakJwt (parse Bearer) · 130 KeycloakTenant (token tenant == `x-okapi-tenant`) · 140 Tenant (enabled?) · 150 KeycloakImpersonation · 160 KeycloakAuthorization (RPT) · 170 SidecarSignature · 171 DesiredPermissions.
- The proxy is reactive end to end (Vert.x WebClient + Mutiny); blocking code does not belong on the request path.

## Pitfalls

- SelfRequest is not a sidecar-to-sidecar bypass. It flags a request that carries this sidecar's own signature, typically a module calling back through its own sidecar, and later filters use the flag to skip auth checks. Calls between different sidecars never match it: the signature is unique per sidecar process and is removed on egress.
- Config: `src/main/resources/application.properties` with env-var substitution; test overrides in `src/test/resources/application.properties`. `README.md` documents most env vars; `application.properties` shows any that are missing there.

## Testing conventions

- Unit: JUnit 5 + Mockito, no Quarkus context; `@UnitTest`, `@ExtendWith(MockitoExtension.class)`.
- Integration: `@IntegrationTest` (includes `@QuarkusTest`); WireMock via `@EnableWireMock`, in-memory Kafka via `InMemoryMessagingExtension`.
- Support classes in `src/test/java/org/folio/sidecar/support/`; WireMock mappings in `src/test/resources/mappings/` (by service); JSON fixtures in `src/test/resources/json/`.
