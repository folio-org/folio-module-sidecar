package org.folio.sidecar.integration.keycloak;

import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.keycloak.configuration.KeycloakProperties;

@ApplicationScoped
@RequiredArgsConstructor
public class JsonWebTokenParser {

  public static final String INVALID_SEGMENTS_JWT_ERROR_MSG = "Invalid amount of segments in JsonWebToken.";
  private static final String TOKEN_SEPARATOR = "\\.";
  private static final String ISSUER_CLAIM = "iss";

  private final OpenidJwtParserProvider openidJwtParserProvider;
  private final KeycloakProperties properties;

  /**
   * Parses json web token string from request to {@link JsonWebToken} object.
   *
   * @param jwt - json web token {@link String} value
   * @return parsed {@link JsonWebToken} object
   * @throws ParseException - if json web token cannot be parsed
   */
  public JsonWebToken parse(String jwt) throws ParseException {
    var issuerAuthTokenTuple = getIssuerAuthTokenTuple(jwt);
    var issuerUri = issuerAuthTokenTuple.getItem1();

    var jwtParser = openidJwtParserProvider.getParser(issuerUri);
    if (jwtParser == null) {
      throw new ParseException("Invalid JsonWebToken issuer.");
    }

    return jwtParser.parse(issuerAuthTokenTuple.getItem2());
  }

  private Tuple2<String, String> getIssuerAuthTokenTuple(String jwt) throws ParseException {
    return Tuple2.of(getTokenIssuer(jwt), jwt);
  }

  @SuppressWarnings("squid:S2129")
  private String getTokenIssuer(String authToken) throws ParseException {
    var split = authToken.split(TOKEN_SEPARATOR);
    if (split.length < 2 || split.length > 3) {
      throw new ParseException(INVALID_SEGMENTS_JWT_ERROR_MSG);
    }

    var payload = (JsonObject) Json.decodeValue(new String(Base64.getDecoder().decode(split[1])));
    var issuer = payload.getString(ISSUER_CLAIM);
    if (issuer == null) {
      throw new ParseException("Issuer not found in the JsonWebToken.");
    }

    if (!issuer.startsWith(properties.getUrl())) {
      throw new ParseException("Invalid JsonWebToken issuer.");
    }

    return issuer;
  }
}
