package org.folio.sidecar.service.routing.lookup;

import java.util.List;
import java.util.Set;

/**
 * Per-tenant egress bootstrap metadata for targeted refresh and redundant-refresh avoidance.
 *
 * @param applicationScope sorted application ID scope used for the last successful bootstrap
 * @param moduleIds provider module IDs present in the tenant's egress bootstrap
 */
public record TenantEgressMetadata(List<String> applicationScope, Set<String> moduleIds) {}
