# folio-module-sidecar

Quarkus sidecar deployed alongside every FOLIO module as a transparent HTTP proxy. Java, Quarkus, Vert.x, Kafka (SmallRye Reactive Messaging), Keycloak JWT (SmallRye JWT).

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn clean -Pfips install       # FIPS-compliant build
mvn test                       # unit tests (@UnitTest)
mvn verify                     # unit and integration tests (@IntegrationTest)
mvn test -Dtest="TenantFilterTest#filter_positive"  # single test
mvn verify -Pcoverage          # coverage report (JaCoCo 80% instruction rule only reports, does not fail the build)
mvn checkstyle:check           # process-classes phase; fails on warnings
```

Dev mode needs env: `SIDECAR_URL`, `MODULE_VERSION`, `MODULE_NAME`, `MODULE_URL`, `AM_CLIENT_URL`, `TE_CLIENT_URL`, `TM_CLIENT_URL` (see `README.md`). Checkstyle enforces **max method length 23 lines** in production code (suppressed in tests).

## Testing Conventions

- **Unit**: JUnit + Mockito, no Quarkus context; `@UnitTest`, `@ExtendWith(MockitoExtension.class)`.
- **Integration**: `@IntegrationTest` (includes `@QuarkusTest`); WireMock via `@EnableWireMock`; in-memory Kafka only where the test sets it up (`InMemoryMessagingExtension` or `InMemoryMessagingTestProfile`); other ITs start Kafka through Quarkus Dev Services, so integration tests need Docker.
- Naming: `methodName_positive/negative_description`.
- Never use lenient Mockito; verify only unmocked interactions; prefer helper methods over blanket `@BeforeEach` stubbing.
- Full unit-testing guide: https://github.com/folio-org/folio-eureka-ai-dev/blob/master/docs/testing/unit-testing.md
