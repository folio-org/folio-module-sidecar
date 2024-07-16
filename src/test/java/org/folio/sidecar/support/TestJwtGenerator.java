package org.folio.sidecar.support;

import static org.folio.sidecar.utils.SecurityUtils.JWT_OKAPI_USER_ID_CLAIM;
import static org.folio.sidecar.utils.SecurityUtils.JWT_SESSION_ID_CLAIM;

import io.jsonwebtoken.Jwts;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.time.DateUtils;
import org.eclipse.microprofile.jwt.Claims;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestJwtGenerator {

  private static final String TEST_KEY_PATH = "keycloak/testkey.der";
  private static final String TEST_KID = "qJr6ysS_hauNBc65Sp16ORFOqJtII3ej6uAP2-jOnuo";

  @SneakyThrows
  public static String generateJwtString(String keycloakUrl, String tenant) {
    var key = readPrivateKey(TestUtils.classpathFile(TEST_KEY_PATH));

    var now = new Date();
    return Jwts.builder()
      .header().add(Claims.kid.name(), TEST_KID)
      .and()
      .subject(UUID.randomUUID().toString())
      .issuer(keycloakUrl + "/realms/" + tenant)
      .issuedAt(now)
      .expiration(DateUtils.addDays(now, 1))
      .signWith(key)
      .compact();
  }

  @SneakyThrows
  public static String generateJwtString(String keycloakUrl, String tenant, String userId) {
    return generateJwtString(keycloakUrl, tenant, userId, UUID.randomUUID());
  }

  @SneakyThrows
  public static String generateJwtString(String keycloakUrl, String tenant, String userId, UUID sessionState) {
    var key = readPrivateKey(TestUtils.classpathFile(TEST_KEY_PATH));

    var now = new Date();
    return Jwts.builder()
      .header().add(Claims.kid.name(), TEST_KID).and()
      .subject(UUID.randomUUID().toString())
      .issuer(keycloakUrl + "/realms/" + tenant)
      .issuedAt(now)
      .expiration(DateUtils.addDays(now, 1))
      .claims(Map.of(JWT_OKAPI_USER_ID_CLAIM, userId, JWT_SESSION_ID_CLAIM, sessionState))
      .signWith(key)
      .compact();
  }

  @SneakyThrows
  public static String generateExpiredJwtToken(String keycloakUrl, String tenant) {
    var key = readPrivateKey(TestUtils.classpathFile(TEST_KEY_PATH));

    var expiredDate = new Date(System.currentTimeMillis() - DateUtils.MILLIS_PER_HOUR);
    return Jwts.builder()
      .header().add(Claims.kid.name(), TEST_KID)
      .and()
      .subject(UUID.randomUUID().toString())
      .issuer(keycloakUrl + "/realms/" + tenant)
      .issuedAt(expiredDate)
      .expiration(expiredDate)
      .signWith(key)
      .compact();
  }

  private static RSAPrivateKey readPrivateKey(File keyFile) throws Exception {
    FileInputStream fis = new FileInputStream(keyFile);
    DataInputStream dis = new DataInputStream(fis);

    byte[] keyBytes = new byte[(int) keyFile.length()];
    dis.readFully(keyBytes);
    dis.close();

    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");

    return (RSAPrivateKey) keyFactory.generatePrivate(spec);
  }
}
