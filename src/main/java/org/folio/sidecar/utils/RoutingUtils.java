package org.folio.sidecar.utils;

import static org.folio.sidecar.integration.okapi.OkapiHeaders.REQUEST_ID;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.SYSTEM_TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.USER_ID;
import static org.folio.sidecar.utils.CollectionUtils.isEmpty;
import static org.folio.sidecar.utils.CollectionUtils.isNotEmpty;

import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.folio.sidecar.integration.okapi.OkapiHeaders;
import org.folio.sidecar.model.ScRoutingEntry;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RoutingUtils {

  public static final String TENANT_INTERFACE = "_tenant";
  public static final String SYS_INTERFACE_TYPE = "system";
  public static final String TIMER_INTERFACE_ID = "_timer";

  /**
   * Key of the {@link ScRoutingEntry routing entry} in the context.
   */
  public static final String SC_ROUTING_ENTRY_KEY = "scRoutingEntry";
  public static final String SELF_REQUEST_KEY = "selfRequest";

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
    var newId = String.format("%06d%s", random.nextInt(1000000), path);
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
   * Updates path removing {@code "/$moduleName"} prefix if it is present.
   *
   * @param rc - routing context for path extraction
   * @param modulePrefixEnabled - configuration that defines if module prefix is enabled or not
   * @param moduleName - module name from configuration
   * @return updated path value without module name prefix
   */
  public static String updatePath(RoutingContext rc, boolean modulePrefixEnabled, String moduleName) {
    var path = rc.request().path();
    if (modulePrefixEnabled) {
      var modulePathPrefix = '/' + moduleName;
      return path.startsWith(modulePathPrefix) ? path.substring(modulePathPrefix.length()) : path;
    }

    return path;
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
    return Optional.ofNullable(rc.get(TOKEN));
  }

  public static void putParsedToken(RoutingContext rc, JsonWebToken token) {
    rc.put(TOKEN, token);
  }

  public static RoutingContext putParsedSystemToken(RoutingContext routingContext, JsonWebToken systemToken) {
    routingContext.put(SYSTEM_TOKEN, systemToken);
    return routingContext;
  }

  public static Optional<JsonWebToken> getParsedSystemToken(RoutingContext rc) {
    return Optional.ofNullable(rc.get(SYSTEM_TOKEN));
  }

  public static void setHeader(RoutingContext rc, String header, String value) {
    rc.request().headers().set(header, value);
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

  public static void setUserIdHeader(RoutingContext rc, String userId) {
    rc.request().headers().set(USER_ID, userId);
  }

  public static Optional<String> getUserIdHeader(RoutingContext rc) {
    return Optional.ofNullable(rc.request().headers().get(USER_ID));
  }

  public static boolean hasPermissionsDesired(RoutingContext rc) {
    return isNotEmpty(getPermissionsDesired(rc));
  }

  public static List<String> getPermissionsDesired(RoutingContext rc) {
    var scRoutingEntry = getScRoutingEntry(rc);
    var endpoint = scRoutingEntry.getRoutingEntry();
    return endpoint.getPermissionsDesired();
  }
}
