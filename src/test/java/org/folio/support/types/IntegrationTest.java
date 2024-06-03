package org.folio.support.types;

import io.quarkus.test.junit.QuarkusTest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks test as integration.
 */
@Tag("integration")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@QuarkusTest
public @interface IntegrationTest {}
