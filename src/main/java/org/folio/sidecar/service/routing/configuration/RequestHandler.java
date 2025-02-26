package org.folio.sidecar.service.routing.configuration;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

@Qualifier
@Documented
@Retention(RUNTIME)
public @interface RequestHandler {
}
