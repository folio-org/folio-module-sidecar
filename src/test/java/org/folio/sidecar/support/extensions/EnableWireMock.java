package org.folio.sidecar.support.extensions;

import io.quarkus.test.common.QuarkusTestResource;
import jakarta.enterprise.inject.Stereotype;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Stereotype
@QuarkusTestResource(value = WiremockResourceConfigurable.class, restrictToAnnotatedClass = true)
public @interface EnableWireMock {

  boolean https() default false;

  boolean verbose() default false;
}

