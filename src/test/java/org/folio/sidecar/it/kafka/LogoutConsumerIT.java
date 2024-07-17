package org.folio.sidecar.it.kafka;

import static java.util.UUID.randomUUID;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.sidecar.integration.kafka.LogoutEvent.Type.LOGOUT;
import static org.mockito.Mockito.verify;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.sidecar.integration.kafka.LogoutConsumer;
import org.folio.sidecar.integration.kafka.LogoutEvent;
import org.folio.sidecar.integration.keycloak.KeycloakImpersonationService;
import org.folio.sidecar.integration.keycloak.filter.KeycloakAuthorizationFilter;
import org.folio.sidecar.support.extensions.EnableWireMock;
import org.folio.sidecar.support.extensions.InMemoryMessagingExtension;
import org.folio.sidecar.support.profile.InMemoryMessagingTestProfile;
import org.folio.support.types.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
@TestProfile(InMemoryMessagingTestProfile.class)
@QuarkusTestResource(value = InMemoryMessagingExtension.class, initArgs = {
  @ResourceArg(value = "incoming", name = "logout")
})
@EnableWireMock
class LogoutConsumerIT {

  @InjectMock KeycloakImpersonationService impersonationService;
  @InjectMock KeycloakAuthorizationFilter keycloakAuthorizationFilter;

  @InjectSpy LogoutConsumer logoutConsumer;

  @Inject @Any InMemoryConnector connector;

  @Test
  void consume_positive() {
    var event = logoutEvent();
    sendEvent(event);

    awaitUntilAsserted(() -> verify(logoutConsumer).consume(event));
    verify(impersonationService).invalidate(event);
    verify(keycloakAuthorizationFilter).invalidate(event);
  }

  private void sendEvent(LogoutEvent event) {
    InMemorySource<LogoutEvent> discoveryIn = connector.source("logout");
    discoveryIn.send(event);
  }

  private static void awaitUntilAsserted(ThrowingRunnable runnable) {
    Awaitility.await()
      .atMost(FIVE_SECONDS)
      .pollDelay(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(runnable);
  }

  private static LogoutEvent logoutEvent() {
    var result = new LogoutEvent();
    result.setUserId(randomUUID().toString());
    result.setSessionId(randomUUID().toString());
    result.setType(LOGOUT);
    return result;
  }
}
