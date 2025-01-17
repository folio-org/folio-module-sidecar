package org.folio.sidecar.configuration;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
@ApplicationScoped
public class VertxConfigLogger {

  private final Vertx vertx;

  public void logVertxConfig(@Observes StartupEvent event) {
    try {
      var vertxImpl = (VertxImpl) vertx;

      var eventLoopGroup = (MultithreadEventExecutorGroup) vertxImpl.getEventLoopGroup();

      var readonlyChildrenField = MultithreadEventExecutorGroup.class.getDeclaredField("children");
      readonlyChildrenField.setAccessible(true);
      var children = readonlyChildrenField.get(eventLoopGroup);

      log.info("Vertx event loop readonly children: {}", ((EventExecutor[]) children).length);
    } catch (Exception e) {
      log.warn("Failed to log Vertx event loop readonly children", e);
    }
  }
}
