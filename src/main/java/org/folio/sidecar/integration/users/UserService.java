package org.folio.sidecar.integration.users;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TENANT;
import static org.folio.sidecar.integration.okapi.OkapiHeaders.TOKEN;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.sidecar.integration.users.configuration.property.ModUsersProperties;
import org.folio.sidecar.integration.users.model.User;
import org.folio.sidecar.service.ServiceTokenProvider;

@Log4j2
@ApplicationScoped
public class UserService {

  private final WebClient webClient;
  private final ModUsersProperties modUsersProperties;
  private final Cache<String, User> userCache;
  private final ServiceTokenProvider serviceTokenProvider;

  public UserService(@Named("webClientTls") WebClient webClient, ModUsersProperties modUsersProperties,
    Cache<String, User> userCache, ServiceTokenProvider serviceTokenProvider) {
    this.webClient = webClient;
    this.modUsersProperties = modUsersProperties;
    this.userCache = userCache;
    this.serviceTokenProvider = serviceTokenProvider;
  }

  public Future<User> findUser(String targetTenant, String userId, RoutingContext rc) {
    var cacheKey = buildKey(userId, targetTenant);
    var userTenant = userCache.getIfPresent(cacheKey);
    if (userTenant != null) {
      return succeededFuture(userTenant);
    }
    return serviceTokenProvider.getServiceToken(rc)
      .flatMap(serviceToken -> findUserById(targetTenant, userId, serviceToken))
      .onSuccess(user -> {
        log.debug("User tenants found: user = {}, targetTenant = {}", userId, targetTenant);
        userCache.put(cacheKey, user);
      })
      .onFailure(error ->
        log.warn("Failed to get user tenants: user = {}, targetTenant = {}", userId, targetTenant, error));
  }

  public Future<List<String>> findUserPermissions(RoutingContext rc, List<String> permissions, String userId,
    String tenant) {
    requireNonNull(permissions, "Permissions must not be null");

    var queryParams = permissions.stream().map(p -> "desiredPermissions=" + p).collect(joining("&"));

    return serviceTokenProvider.getServiceToken(rc)
      .flatMap(serviceToken -> findPermissionsByQuery(userId, tenant, queryParams, serviceToken))
      .onFailure(error -> log.warn("Failed to find user permissions: user = {}, tenant = {}", userId, tenant, error));
  }

  private Future<List<String>> findPermissionsByQuery(String userId, String tenant,
    String queryParams, String token) {
    return webClient.getAbs(buildUserPermissionsPath(modUsersProperties.getUrl(), userId) + "?" + queryParams)
      .putHeader(TENANT, tenant)
      .putHeader(TOKEN, token)
      .send()
      .map(response -> response.bodyAsJson(PermissionContainer.class).permissions());
  }

  private Future<User> findUserById(String targetTenant, String userId, String serviceToken) {
    return webClient.getAbs(buildUsersUrl(modUsersProperties.getUrl(), userId))
      .putHeader(TENANT, targetTenant)
      .putHeader(TOKEN, serviceToken)
      .send()
      .flatMap(this::processResponse);
  }

  private Future<User> processResponse(HttpResponse<Buffer> response) {
    if (response.statusCode() != 200) {
      log.error("User tenants not found: status = {}, body = {}", response.statusCode(), response.bodyAsString());
      return failedFuture("User tenants not found");
    }
    return succeededFuture(response.bodyAsJson(User.class));
  }

  private static String buildUserPermissionsPath(String modUsersUrl, String userId) {
    return String.format("%s/users/%s/permissions", modUsersUrl, userId);
  }

  private static String buildUsersUrl(String modUsersUrl, String userId) {
    return modUsersUrl + "/users/" + userId;
  }

  private static String buildKey(String userId, String tenant) {
    return userId + "#" + tenant;
  }

  public record PermissionContainer(List<String> permissions) {
  }
}
