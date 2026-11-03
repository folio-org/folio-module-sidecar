package org.folio.sidecar.support;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.jboss.logmanager.ExtLogRecord;

/** Captures only diagnostic records, restoring the logger after a focused test. */
public final class OutboundLogCapture implements AutoCloseable {

  private final Logger logger = Logger.getLogger("org.folio.sidecar.outbound");
  private final Level previous = logger.getLevel();
  private final List<String> messages = new ArrayList<>();
  private final Handler handler = new Handler() {
    @Override
    public void publish(LogRecord record) {
      messages.add(((ExtLogRecord) record).getFormattedMessage());
    }

    @Override
    public void flush() { }

    @Override
    public void close() { }
  };

  public OutboundLogCapture() {
    logger.setLevel(Level.FINE);
    logger.addHandler(handler);
  }

  public List<String> messages() {
    return messages;
  }

  @Override
  public void close() {
    logger.removeHandler(handler);
    logger.setLevel(previous);
  }
}
