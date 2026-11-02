# folio-module-sidecar

Quarkus sidecar that runs next to one FOLIO backend module: it authenticates and authorizes ingress requests before forwarding them to `MODULE_URL`, and adds service and system-user tokens to egress module-to-module calls.

## Commands

```bash
mvn install -DskipTests        # build
mvn test                       # unit tests (@UnitTest, *Test.java)
mvn verify                     # unit and integration tests (@IntegrationTest, *IT.java); needs Docker
mvn test -Dtest=TenantFilterTest#filter_positive  # one unit test
mvn verify -Dit.test=SidecarIT -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # one IT, unit tests skipped
mvn checkstyle:check           # style only
mvn install -Pfips             # FIPS build (BouncyCastle FIPS dependencies)
mvn package -Pnative           # native executable; needs GraalVM/Mandrel or the container-build flags in README.md
mvn verify -Pcoverage          # JaCoCo report; the 80% instruction rule only reports, it does not fail the build
```

Every lifecycle build cleans `target/` first, because `maven-clean-plugin` is bound to `initialize`. Integration tests need Docker: an IT whose profile is not `InMemoryMessagingTestProfile` or a subclass (for example `SidecarIT` with `CommonIntegrationTestProfile`) starts Kafka through Quarkus Dev Services. Surefire and failsafe fix `groups` to `unit` and `integration` in `pom.xml`, so `-Dgroups` on the command line has no effect.

Checkstyle runs at `process-classes` and fails the build on warnings, test sources included. The base rules come from the `folio-java-checkstyle` artifact. This repository sets method length to 23 lines (`checkstyle/checkstyle-checker.properties`) and suppresses only `MethodLength` for tests.

Dev mode (`mvn quarkus:dev`) does not start without `MODULE_NAME`, `MODULE_VERSION`, and `SIDECAR_URL`, because they have no defaults. `README.md` has a full example.

## Conventions

- Request filters and handlers run on the Vert.x event loop and return `io.vertx.core.Future`. Wrap blocking work in `vertx.executeBlocking`, as `AsyncSecureStoreProxy` and `AsyncJsonWebTokenParser` do, because a blocking call stalls every request on that loop.
- Classes that Jackson creates (the `model` packages) need `@RegisterForReflection`; third-party classes go in `src/main/resources/reflection-config.json`. No test runs against the native image (`@QuarkusIntegrationTest`), so a missing entry passes every test and breaks only the native build.
- A test class runs only when its tag and name match: `@UnitTest` with a `*Test` name, or `@IntegrationTest` with a `*IT` name. `WebClientConfigurationTest` has `@QuarkusTest` without a tag, so no build runs it.

## Gotchas

- Routing picks the handler by path and method, not by caller. Ingress lookup runs before egress, so a module that calls one of its own endpoints through its sidecar goes through the ingress filter chain.
- Such a call carries this sidecar's signature (`x-okapi-sidecar-signature`). `SidecarSignatureFilter` adds it to every request forwarded to the module, and it is removed on egress and from responses. The value is a hash created once per process, so only this process matches it. When `SelfRequestFilter` sees it, `KeycloakTenantFilter`, `TenantFilter`, `KeycloakImpersonationFilter`, and `KeycloakAuthorizationFilter` are skipped. `KeycloakJwtFilter` still runs: it accepts a missing token but rejects an invalid one.
- Filter skip rules are in each filter's `shouldSkip`. System-interface requests (for example `_tenant`) skip JWT parsing, the token-tenant check, and authorization. `_timer` is also a system interface, but it still goes through all three. Empty `permissionsRequired` means no token is needed, but a token that fails to parse is still rejected. Exactly `["*"]` means a valid token is needed but Keycloak authorization is skipped.
- `RoutingUtils.hasSystemAccessToken` only checks that an `X-System-Token` header exists. `KeycloakSystemJwtFilter` does not validate it on system-interface requests, and `DesiredPermissionsFilter` keeps the caller's `X-Okapi-Permissions` whenever the header exists.
- `DesiredPermissionsFilter` logs and ignores errors from the permission lookup, so the request continues without the desired permissions. Only `ModUsersKeycloakTargetNotResolvedException` fails the request.
- `ModuleEntitlementHandler` (`routing.module-entitlement.enabled`, on by default) answers `GET /entitlements/modules/{moduleId}` with the enabled tenants before ingress routing, so no ingress filter runs for it.
- If loading the module bootstrap from mgr-applications fails after retries, `RoutingService` stops the process with exit code 1. When tenant-scoped routing is off, this also applies to the re-fetch after a discovery event. Until routes are loaded, requests get 503.
- A request for a tenant that is not enabled triggers a reload of tenants and entitlements, at most once per `tenant-service.reset-task.cron-definition` period (5 minutes by default). While the reload runs, requests for all tenants get 503 (`EntitlementsNotLoadedException`).
- An exception thrown from an `@Incoming` Kafka method stops that channel for the process, because no failure strategy is configured. Each instance uses its own consumer group (`kafka.group.id` includes `quarkus.uuid`), so every instance receives every event.
- Egress always sets `X-System-Token`. It fetches a system-user token only when `X-Okapi-Token` is missing, blank, or `"null"`; a failure fails the request unless `handler.egress.ignore-system-user-token-error=true`. A 401 from the target evicts the cached service token for the tenant, without a retry.
- `routing.forward-to-gateway.enabled` defaults to the value of `SIDECAR_TENANT_SCOPED_ROUTING_ENABLED`, so enabling tenant-scoped routing also forwards unknown requests to the gateway unless `SIDECAR_FORWARD_UNKNOWN_REQUESTS` is set. In tenant-scoped mode, startup loads only ingress routes and `EgressBootstrapService` builds egress routes per tenant.
