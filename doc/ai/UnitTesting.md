# Unit Testing Guidelines

This document provides guidelines and best practices for writing unit tests in the project.

## Table of Contents
- [General Principles](#general-principles)
- [Mockito Best Practices](#mockito-best-practices)
- [Test Structure](#test-structure)
- [Verification Patterns](#verification-patterns)
- [Common Patterns](#common-patterns)
- [Examples](#examples)

## General Principles

### 1. Test Independence
- Each test should be independent and self-contained
- Tests should not rely on execution order
- Use local variables within test methods instead of shared instance variables when possible

### 2. Clear Test Names
Use descriptive test method names that follow the pattern:
```
methodName_scenario_expectedBehavior
```

Examples:
- `tableExists_positive_tableIsPresent`
- `tableExists_negative_sqlExceptionWhenGettingConnection`
- `deleteTimer_negative_timerDescriptorIdIsNull`

### 3. Test Organization
Structure tests with clear sections:
1. **Arrange**: Set up test data and mocks
2. **Act**: Execute the method under test
3. **Assert**: Verify the expected outcome

## Mockito Best Practices

### 1. Never Use Lenient Mode
❌ **AVOID**:
```java
@MockitoSettings(strictness = Strictness.LENIENT)
```

✅ **PREFER**: Write precise tests that only stub what they need.

### 2. Avoid Unnecessary Stubbing
Only set up mocks (`when()`) that are actually used in the test.

❌ **AVOID**:
```java
@BeforeEach
void setUp() {
    // Setting up mocks for ALL tests, even if some don't need them
    when(context.getFolioModuleMetadata()).thenReturn(moduleMetadata);
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(moduleMetadata.getDBSchemaName(TENANT_ID)).thenReturn(SCHEMA_NAME);
}
```

✅ **PREFER**: Use a helper method and call it only in tests that need it:
```java
private void setupContextMocks() {
    when(context.getFolioModuleMetadata()).thenReturn(moduleMetadata);
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(moduleMetadata.getDBSchemaName(TENANT_ID)).thenReturn(SCHEMA_NAME);
}

@Test
void testThatNeedsContext() {
    setupContextMocks(); // Only called when needed
    // ... rest of test
}

@Test
void testThatDoesNotNeedContext() {
    // No context setup needed
    // ... rest of test
}
```

### 3. Remove Redundant Verify Statements
Only verify interactions that are NOT mocked with `when()`.

❌ **AVOID**:
```java
when(dataSource.getConnection()).thenReturn(connection);
when(connection.getMetaData()).thenReturn(databaseMetaData);
when(resultSet.next()).thenReturn(true);

// ... test execution ...

verify(dataSource).getConnection();       // Redundant - already mocked
verify(connection).getMetaData();         // Redundant - already mocked
verify(resultSet).next();                 // Redundant - already mocked
verify(connection).close();               // Good - NOT mocked
verify(resultSet).close();                // Good - NOT mocked
```

✅ **PREFER**: Only verify unmocked interactions:
```java
when(dataSource.getConnection()).thenReturn(connection);
when(connection.getMetaData()).thenReturn(databaseMetaData);
when(resultSet.next()).thenReturn(true);

// ... test execution ...

verify(connection).close();    // Only verify what's NOT mocked
verify(resultSet).close();
```

**Rationale**: If you're mocking a method with `when()`, you already know it will be called. Verifying it adds no value and creates maintenance burden.

### 4. Use verifyNoMoreInteractions Carefully
When using `verifyNoMoreInteractions()` in `@AfterEach`, only include mocks that should have NO unexpected interactions:

```java
@AfterEach
void tearDown() {
    // Only include mocks that should be fully accounted for
    verifyNoMoreInteractions(dataSource, connection, databaseMetaData, resultSet);
    // Don't include context/moduleMetadata if they're used via helper methods
}
```

## Test Structure

### 1. Test Class Setup
```java
@UnitTest
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    private static final String CONSTANT_VALUE = "test-value";

    @Mock
    private Dependency1 dependency1;

    @Mock
    private Dependency2 dependency2;

    // Helper method for common mock setup
    private void setupCommonMocks() {
        when(dependency1.getSomething()).thenReturn("value");
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(dependency1, dependency2);
    }
}
```

### 2. Individual Test Structure
```java
@Test
void methodName_scenario_expectedBehavior() throws Exception {
    // Arrange: Setup mocks and test data
    setupCommonMocks(); // If needed
    var service = new MyService(dependency1, dependency2);

    when(dependency1.doSomething()).thenReturn(expectedValue);

    // Act: Execute the method under test
    var result = service.methodUnderTest();

    // Assert: Verify the outcome
    assertThat(result).isEqualTo(expectedValue);
    verify(dependency1).close(); // Only verify unmocked calls
}
```

## Verification Patterns

### 1. Testing Successful Operations
```java
@Test
void operation_positive_success() throws Exception {
    setupRequiredMocks();
    var service = new MyService(dependencies);

    when(repository.find()).thenReturn(entity);

    var result = service.operation();

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(entity.getId());
    // Only verify unmocked interactions
    verify(connection).close();
}
```

### 2. Testing Exception Scenarios
```java
@Test
void operation_negative_throwsException() throws Exception {
    var service = new MyService(dependencies);
    var expectedException = new SQLException("Connection failed");

    when(dataSource.getConnection()).thenThrow(expectedException);

    assertThatThrownBy(() -> service.operation())
        .isInstanceOf(DataRetrievalFailureException.class)
        .hasMessageContaining("Failed to perform operation")
        .hasCause(expectedException);

    // No need to verify mocked methods
}
```

### 3. Testing with Multiple Scenarios
When testing different scenarios (e.g., different case formats), instantiate the service locally:

```java
@Test
void operation_withUpperCase_success() throws Exception {
    setupCommonMocks();
    var service = new MyService(dependency, CaseFormat.UPPER);

    when(repository.find(eq("TABLENAME"))).thenReturn(entity);

    var result = service.operation();

    assertThat(result).isTrue();
    verify(connection).close();
}

@Test
void operation_withLowerCase_success() throws Exception {
    setupCommonMocks();
    var service = new MyService(dependency, CaseFormat.LOWER);

    when(repository.find(eq("tablename"))).thenReturn(entity);

    var result = service.operation();

    assertThat(result).isTrue();
    verify(connection).close();
}
```

## Common Patterns

### 1. Parameterized Tests with Single Parameter
When testing methods that should fail for multiple input values, you can use a simplified pattern:

```java
@ParameterizedTest
@MethodSource("invalidInputsProvider")
void operation_negative_throwsException(String invalidInput) {
    assertThatThrownBy(() -> service.operation(invalidInput))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid input");
}

private static Stream<String> invalidInputsProvider() {
    return Stream.of(
        "invalid-1",
        "invalid-2",
        "invalid-3"
    );
}
```

**Note**: Use `Stream<String>` (or appropriate type) directly instead of `Stream<Arguments>` when testing a single parameter.

### 2. Explicit Type Declaration for Lambdas
When var type inference fails with lambda expressions (e.g., Supplier), use explicit type declaration:

```java
@Test
void operation_positive() {
    // ❌ AVOID: var may fail to infer lambda type
    // var supplier = () -> new IllegalStateException("error");

    // ✅ PREFER: Explicit type declaration
    Supplier<RuntimeException> supplier = () -> new IllegalStateException("error");

    var result = service.operation(supplier);
    assertThat(result).isNotNull();
}
```

### 3. Testing Exact Matching Logic
When testing filtering/matching logic where similar values exist but only exact matches should be selected:

```java
@Test
void findModule_positive_exactMatchWithSimilarNames() {
    // Arrange: Multiple similar module names
    var modules = List.of(
        "mod-foo-1.0.0",        // Exact match for "mod-foo"
        "mod-foo-item-1.0.0",   // Similar but not exact match
        "mod-foobar-1.0.0"      // Similar but not exact match
    );

    // Act: Search for exact name
    var result = service.findModuleByName("mod-foo", modules);

    // Assert: Only exact match is returned
    assertThat(result).isEqualTo("mod-foo-1.0.0");
}
```

**Key Points**:
- Include similar values that should NOT match
- Verify that only the exact match is selected
- Test helps prevent accidental substring or partial matching bugs

### 4. Testing Composed Logic with Utility Methods
When testing code that uses utility methods with multiple error paths (e.g., `CollectionUtils.takeOne()`):

```java
@Test
void findModule_negative_noModulesFound() {
    // Test the "empty collection" error path
    var emptyModules = List.of();

    assertThatThrownBy(() -> service.findModuleByName("mod-foo", emptyModules))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No modules found");
}

@Test
void findModule_negative_multipleModulesFound() {
    // Test the "too many items" error path
    var duplicateModules = List.of("mod-foo-1.0.0", "mod-foo-2.0.0");

    assertThatThrownBy(() -> service.findModuleByName("mod-foo", duplicateModules))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Multiple modules found");
}
```

**Rationale**: Ensure all error paths from utility methods are covered in the composed logic tests.

### 5. Testing with Database Resources
Always verify that resources are closed:
```java
@Test
void testDatabaseOperation() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(any(), any(), any(), any())).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);

    boolean result = service.checkTable();

    assertThat(result).isTrue();
    verify(connection).close();  // Ensure connection is closed
    verify(resultSet).close();   // Ensure result set is closed
}
```

### 6. Testing with Context Dependencies
```java
private void setupContextMocks() {
    when(context.getFolioModuleMetadata()).thenReturn(moduleMetadata);
    when(context.getTenantId()).thenReturn(TENANT_ID);
    when(moduleMetadata.getDBSchemaName(TENANT_ID)).thenReturn(SCHEMA_NAME);
}

@Test
void testWithContext() {
    setupContextMocks();
    var service = new MyService(dataSource, context);

    // Test execution
}
```

### 7. Testing Null Validation
```java
@Test
void operation_negative_parameterIsNull() {
    var service = new MyService(dependencies);

    assertThatThrownBy(() -> service.operation(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter must not be null");
}
```

## Examples

### Example 1: Complete Test Class
```java
@UnitTest
@ExtendWith(MockitoExtension.class)
class TimerTableCheckServiceTest {

    private static final String TIMER_TABLE_NAME = "timer";
    private static final String[] TABLE_TYPE = {"TABLE"};
    private static final String SCHEMA_NAME = TENANT_ID + "_mod_scheduler";

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseMetaData databaseMetaData;
    @Mock
    private ResultSet resultSet;
    @Mock
    private FolioExecutionContext context;
    @Mock
    private FolioModuleMetadata moduleMetadata;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(dataSource, connection, databaseMetaData, resultSet);
    }

    @Test
    void tableExists_positive_tableIsPresent() throws SQLException {
        setupContextMocks();
        var service = new TimerTableCheckService(dataSource, context);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq(TIMER_TABLE_NAME), eq(TABLE_TYPE)))
            .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        boolean result = service.tableExists();

        assertThat(result).isTrue();
        verify(connection).close();
        verify(resultSet).close();
    }

    @Test
    void tableExists_negative_sqlExceptionWhenGettingConnection() throws SQLException {
        var service = new TimerTableCheckService(dataSource, context);

        var expectedException = new SQLException("Failed to get connection");
        when(dataSource.getConnection()).thenThrow(expectedException);

        assertThatThrownBy(() -> service.tableExists())
            .isInstanceOf(DataRetrievalFailureException.class)
            .hasMessageContaining("Failed to check if table " + TIMER_TABLE_NAME + " exists")
            .hasCause(expectedException);
    }

    private void setupContextMocks() {
        when(context.getFolioModuleMetadata()).thenReturn(moduleMetadata);
        when(context.getTenantId()).thenReturn(TENANT_ID);
        when(moduleMetadata.getDBSchemaName(TENANT_ID)).thenReturn(SCHEMA_NAME);
    }
}
```

### Example 2: Testing with Different Configurations
```java
@Test
void operation_withUpperCase_success() throws SQLException {
    setupContextMocks();
    var service = new MyService(dataSource, context, TableNameCase.UPPER);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getTables(isNull(), eq(SCHEMA_NAME), eq("TIMER"), eq(TABLE_TYPE)))
        .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);

    boolean result = service.tableExists();

    assertThat(result).isTrue();
    verify(connection).close();
    verify(resultSet).close();
}
```

## Checklist

Before committing your tests, verify:

- [ ] No `@MockitoSettings(strictness = Strictness.LENIENT)` annotation
- [ ] No unnecessary stubbing (all `when()` statements are used)
- [ ] Only verify methods that are NOT mocked
- [ ] Test names follow the `methodName_scenario_expectedBehavior` pattern
- [ ] Each test is independent and can run in isolation
- [ ] Resources (connections, streams, etc.) are verified to be closed
- [ ] Exception tests verify the exception type, message, and cause
- [ ] Helper methods are used for common mock setups
- [ ] Tests are organized with clear Arrange-Act-Assert sections
- [ ] Parameterized tests use simplified `Stream<Type>` when testing single parameters
- [ ] Explicit type declarations used for lambdas when var type inference fails
- [ ] Exact matching tests include similar values that should NOT match
- [ ] All error paths from utility methods are covered in composed logic tests

## Summary

**Key Takeaways:**
1. **Never use lenient mode** - write precise tests instead
2. **Only stub what you need** - use helper methods for common setups
3. **Only verify unmocked interactions** - verifying mocked calls is redundant
4. **Keep tests independent** - each test should stand alone
5. **Close resources** - always verify that connections, streams, etc. are closed
6. **Clear naming** - test names should describe the scenario and expected outcome
7. **Simplify parameterized tests** - use `Stream<Type>` for single parameters instead of `Stream<Arguments>`
8. **Explicit types for lambdas** - declare types explicitly when var inference fails
9. **Test exact matching** - include similar values to verify only exact matches are selected
10. **Cover all error paths** - test all exception scenarios from utility methods in composed logic
