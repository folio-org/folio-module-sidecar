package org.folio.sidecar.service;

import static java.lang.System.currentTimeMillis;
import static java.security.MessageDigest.getInstance;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.exception.SidecarSignatureError;

@Log4j2
@ApplicationScoped
public class SidecarSignatureService {

  private static final String SIGNATURE_HEADER = "x-okapi-sidecar-signature";
  private static final String SIGNATURE = generateSignature();

  public boolean isSelfRequest(RoutingContext context) {
    var signature = context.request().getHeader(SIGNATURE_HEADER);
    return SIGNATURE.equals(signature);
  }

  public void removeSignature(RoutingContext context) {
    context.request().headers().remove(SIGNATURE_HEADER);
  }

  public void removeSignature(HttpServerResponse response) {
    response.headers().remove(SIGNATURE_HEADER);
  }

  public RoutingContext populateSignature(RoutingContext rc) {
    rc.request().headers().set(SIGNATURE_HEADER, SIGNATURE);
    return rc;
  }

  private static String generateSignature() {
    long timestamp = currentTimeMillis();
    try {
      var sha256 = getInstance("SHA-256");
      sha256.update(String.valueOf(timestamp).getBytes());
      byte[] hash = sha256.digest();
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      log.error("Error generating sidecar signature", e);
      throw new SidecarSignatureError("Error generating sidecar signature", e);
    }
  }
}
