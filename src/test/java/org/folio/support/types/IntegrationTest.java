package org.folio.support.types;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Stereotype;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks test as integration.
 */
@Tag("integration")
@QuarkusTest
@Stereotype
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface IntegrationTest {}
