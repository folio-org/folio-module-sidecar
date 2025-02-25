package org.folio.sidecar.utils;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.endsWithIgnoreCase;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.SYSTEM_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.USER_ID;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.CollectionUtils.isNotEmpty;
import static org.folio.sidecar.utils.TokenUtils.tokenHash;

import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Supplier;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RoutingUtils {

  public static final String TENANT_INTERFACE = "_tenant";
  public static final String SYS_INTERFACE_TYPE = "system";
  public static final String MULTIPLE_INTERFACE_TYPE = "multiple";
  public static final String TIMER_INTERFACE_ID = "_timer";

  /**
   * Key of the {@link ScRoutingEntry routing entry} in the context.
   */
  public static final String SC_ROUTING_ENTRY_KEY = "scRoutingEntry";
  public static final String SELF_REQUEST_KEY = "selfRequest";
  public static final String ORIGIN_TENANT = "originTenant";
  public static final String PARSED_TOKEN = "parsedToken";
  private static final int URI_MAX_LENGTH = 512;

  /**
   * Generates request id for ingress request.
   *
   * @param rc - routing context as {@link RoutingContext} object
   * @return generated X-Okapi-Request-Id header.
   */
  public static String getRequestId(RoutingContext rc) {
    var request = rc.request();
    var path = request.path();
    path = path.replaceFirst("^(/_)?(/[^/?]+).*$", "$2");
    var random = ThreadLocalRandom.current(); //NOSONAR - random is used for requestId generation
    var newId = format("%06d%s", random.nextInt(1000000), path);
    var currentId = request.getHeader(REQUEST_ID);

    var requestId = StringUtils.isEmpty(currentId) ? newId : currentId + ";" + newId;
    log.debug("Generated requestId: {} [method: {}, path: {}]", requestId, request.method(), request.path());
    return requestId;
  }

  /**
   * Puts {@link ScRoutingEntry routing entry} to the context.
   *
   * @param rc routing context
   * @param scRoutingEntry routing entry
   */
  public static void putScRoutingEntry(RoutingContext rc, ScRoutingEntry scRoutingEntry) {
    rc.put(SC_ROUTING_ENTRY_KEY, scRoutingEntry);
  }

  /**
   * Gets {@link ScRoutingEntry routing entry} from the context.
   *
   * @param rc routing context
   * @return routing entry
   */
  public static ScRoutingEntry getScRoutingEntry(RoutingContext rc) {
    return rc.get(SC_ROUTING_ENTRY_KEY);
  }

  /**
   * Builds path with {@code "/$moduleName"} prefix if it is not present.
   *
   * @param context - routing context for path extraction
   * @param modulePrefixEnabled - configuration that defines if module prefix is enabled or not
   * @param moduleName - module name from configuration
   * @return updated path as {@link String} value with module name prefix
   */
  public static String buildPathWithPrefix(RoutingContext context, boolean modulePrefixEnabled, String moduleName) {
    var path = context.request().path();
    if (modulePrefixEnabled) {
      var modulePathPrefix = '/' + moduleName;
      return !path.startsWith(modulePathPrefix) ? modulePathPrefix + path : path;
    }

    return path;
  }

  /**
   * Retrieves tenant name from {@link OkapiHeaders#TENANT} header.
   *
   * @param rc routing context
   * @return tenant name
   */
  public static String getTenant(RoutingContext rc) {
    return rc.request().getHeader(TENANT);
  }

  public static Optional<JsonWebToken> getParsedToken(RoutingContext rc) {
    return ofNullable(rc.get(PARSED_TOKEN));
  }

  public static void putParsedToken(RoutingContext rc, JsonWebToken token) {
    rc.put(PARSED_TOKEN, token);
  }

  public static RoutingContext putParsedSystemToken(RoutingContext routingContext, JsonWebToken systemToken) {
    routingContext.put(SYSTEM_TOKEN, systemToken);
    return routingContext;
  }

  public static Optional<JsonWebToken> getParsedSystemToken(RoutingContext rc) {
    return ofNullable(rc.get(SYSTEM_TOKEN));
  }

  public static String getHeader(RoutingContext rc, String header) {
    return rc.request().headers().get(header);
  }

  public static void setHeader(RoutingContext rc, String header, String value) {
    rc.request().headers().set(header, value);
  }

  public static void removeHeader(RoutingContext rc, String header) {
    rc.request().headers().remove(header);
  }

  public static boolean isSystemRequest(RoutingContext rc) {
    var scRoutingEntry = getScRoutingEntry(rc);
    return SYS_INTERFACE_TYPE.equals(scRoutingEntry.getInterfaceType());
  }

  public static boolean isTimerRequest(RoutingContext rc) {
    var scRoutingEntry = getScRoutingEntry(rc);
    return TIMER_INTERFACE_ID.equals(scRoutingEntry.getInterfaceId());
  }

  public static boolean isSelfRequest(RoutingContext rc) {
    return Boolean.TRUE.equals(rc.get(SELF_REQUEST_KEY));
  }

  public static boolean hasNoPermissionsRequired(RoutingContext rc) {
    var scRoutingEntry = getScRoutingEntry(rc);
    var endpoint = scRoutingEntry.getRoutingEntry();
    return isEmpty(endpoint.getPermissionsRequired());
  }

  public static boolean isTenantInstallRequest(RoutingContext rc) {
    var scRoutingEntry = getScRoutingEntry(rc);
    return TENANT_INTERFACE.equals(scRoutingEntry.getInterfaceId());
  }

  public static boolean hasSystemAccessToken(RoutingContext rc) {
    var systemToken = rc.request().headers().get(SYSTEM_TOKEN);
    return systemToken != null;
  }

  public static boolean hasUserIdHeader(RoutingContext rc) {
    return rc.request().headers().contains(USER_ID);
  }

  public static boolean hasHeader(RoutingContext rc, String header) {
    return rc.request().headers().contains(header);
  }

  public static boolean hasHeaderWithValue(RoutingContext rc, String header, boolean ensureNonNullValue) {
    if (!hasHeader(rc, header)) {
      return false;
    }
    var headerValue = rc.request().getHeader(header);
    return !StringUtils.isBlank(headerValue) && (!ensureNonNullValue || !headerValue.trim().equalsIgnoreCase("null"));
  }

  public static void setUserIdHeader(RoutingContext rc, String userId) {
    rc.request().headers().set(USER_ID, userId);
  }

  public static Optional<String> getUserIdHeader(RoutingContext rc) {
    return ofNullable(rc.request().headers().get(USER_ID));
  }

  public static boolean hasPermissionsDesired(RoutingContext rc) {
    return isNotEmpty(getPermissionsDesired(rc));
  }

  public static List<String> getPermissionsDesired(RoutingContext rc) {
    var scRoutingEntry = getScRoutingEntry(rc);
    var endpoint = scRoutingEntry.getRoutingEntry();
    return endpoint.getPermissionsDesired();
  }

  public static void putOriginTenant(RoutingContext rc, JsonWebToken token) {
    rc.put(ORIGIN_TENANT, JwtUtils.getOriginTenant(token));
  }

  public static String getOriginTenant(RoutingContext rc) {
    return rc.get(ORIGIN_TENANT);
  }

  public static Supplier<String> dumpContextData(RoutingContext rc) {
    return () -> dumpStream(rc.data().entrySet().stream());
  }

  public static Supplier<String> dumpHeaders(RoutingContext rc) {
    return () -> dumpStream(rc.request().headers().entries().stream());
  }

  public static Supplier<String> dumpUri(RoutingContext rc) {
    return () -> truncate(rc.request().uri(), URI_MAX_LENGTH);
  }

  private static <K, V> String dumpStream(Stream<Entry<K, V>> stream) {
    return stream
      .map(entryToString())
      .reduce((a, b) -> a + "\n" + b)
      .orElse("");
  }

  private static <K, V> Function<Entry<K, V>, String> entryToString() {
    return entry -> {
      var key = entry.getKey();
      var value = entry.getValue();

      if (key instanceof String && endsWithIgnoreCase(key.toString(), "token")) {
        var tokenHashed = tokenHash(value.toString());
        return format("%s = %s", key, tokenHashed);
      }

      return format("%s = %s", key, value);
    };
  }
}
