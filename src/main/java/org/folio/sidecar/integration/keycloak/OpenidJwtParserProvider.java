package org.folio.sidecar.integration.keycloak;

import static org.folio.sidecar.model.EntitlementsEvent.ENTITLEMENTS_EVENT;

import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.model.EntitlementsEvent;
import org.folio.sidecar.service.auth.JwtParserProvider;

@Log4j2
@ApplicationScoped
public class OpenidJwtParserProvider implements JwtParserProvider {

  private final Map<String, JWTParser> tokenParsers = new ConcurrentHashMap<>();

  @Override
  public JWTParser getParser(String issuerUri) {
    var jwtTokenParserProvider = tokenParsers.get(issuerUri);
    if (jwtTokenParserProvider != null) {
      return jwtTokenParserProvider;
    }

    var jwtAuthContextInfo = new JWTAuthContextInfo(issuerUri + "/protocol/openid-connect/certs", issuerUri);
    jwtAuthContextInfo.setForcedJwksRefreshInterval(60);
    jwtAuthContextInfo.setJwksRefreshInterval(60);
    var jwtParser = new DefaultJWTParser(jwtAuthContextInfo);
    tokenParsers.put(issuerUri, jwtParser);
    return jwtParser;
  }

  @SuppressWarnings("unused")
  @ConsumeEvent(value = ENTITLEMENTS_EVENT, blocking = true)
  public void syncCache(EntitlementsEvent event) {
    log.info("Invalidating outdated token parsers");
    var tenants = event.getTenants();
    tokenParsers.keySet().stream()
      .filter(issuer -> !tenants.contains(resolveTenant(issuer)))
      .forEach(tokenParsers::remove);
  }

  private static String resolveTenant(String issuer) {
    return issuer.substring(issuer.lastIndexOf('/') + 1);
  }
}
