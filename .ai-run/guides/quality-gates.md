# Quality Gates

Maven (Java 21, Quarkus 3.x) gates, ordered fastest-to-slowest. Sourced from
`AGENTS.md`, `pom.xml`, and `.github/workflows/maven.yml`. Run the cheap gates
locally before pushing; CI (`folio-org/.github` reusable Maven workflow) runs the
full build, SonarCloud scan, and Docker build.

### Checkstyle

Enforces the FOLIO style rules (external `folio-java-checkstyle` artifact) plus a
**23-line max method length** in production code. Bound to the `process-classes`
phase during a normal build; can be run standalone.

- **Run**: `mvn checkstyle:check`
- **Pass**: `BUILD SUCCESS`, no violations reported.
- **Fail**: `BUILD FAILURE` with `[ERROR] ... Checkstyle` lines naming file, line,
  and rule (e.g. method longer than 23 lines, import order, line length). Any
  warning fails the build — including test sources.
- **Skip if**: never skip for production code changes; the 23-line limit applies
  to `src/main/java` only (suppressed for `src/test/java`).

### Unit tests

JUnit 5 + Mockito, no Quarkus context (`@UnitTest`).

- **Run**: `mvn test -Dgroups=unit`
- **Single test**: `mvn test -Dgroups=unit -Dtest="ClassName#method"`
- **Pass**: `BUILD SUCCESS`, `Tests run: N, Failures: 0, Errors: 0`.
- **Fail**: `BUILD FAILURE`; failing test class/method and assertion shown in the
  surefire output and `target/surefire-reports/`.

### Integration tests

`@IntegrationTest` (`@QuarkusTest`) with WireMock and in-memory Kafka.

- **Run**: `mvn verify -Dgroups=integration`
- **Pass**: `BUILD SUCCESS`, failsafe reports green.
- **Fail**: `BUILD FAILURE`; see `target/failsafe-reports/`. Slower — boots a
  Quarkus test context per class.
- **Skip if**: the change touches only unit-tested logic with no
  filter/routing/integration surface; note the skip in the QA report.

### Coverage

JaCoCo aggregate (unit + integration), 80% instruction minimum.

- **Run**: `mvn verify -Pcoverage`
- **Pass**: build completes; aggregate instruction coverage ≥ 80%
  (`target/site/jacoco-aggregate/`). `haltOnFailure` is false, so it reports
  rather than fails the local build, but keep new code covered.
- **Fail**: coverage check logs the covered ratio below 80%; SonarCloud enforces
  the gate in CI.

### Full build

Compile + checkstyle + tests + uber-jar packaging — the definitive local gate.

- **Run**: `mvn clean install`
- **Skip tests** (compile/package only): `mvn clean install -DskipTests`
- **FIPS build**: `mvn clean -Pfips install`
- **Pass**: `BUILD SUCCESS`; runnable `target/*-runner.jar` produced.
- **Fail**: `BUILD FAILURE`; the first failing phase (compile, checkstyle, or
  test) names the cause.
