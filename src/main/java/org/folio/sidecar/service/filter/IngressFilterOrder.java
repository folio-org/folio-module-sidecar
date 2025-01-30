package org.folio.sidecar.service.filter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum IngressFilterOrder {

  HEADER_VALIDATION(90),
  SELF_REQUEST(100),
  KEYCLOAK_SYSTEM_JWT(110),
  KEYCLOAK_JWT(120),
  KEYCLOAK_TENANT(130),
  TENANT(140),
  KEYCLOAK_IMPERSONATION(150),
  KEYCLOAK_AUTHORIZATION(160),
  SIDECAR_SIGNATURE(170),
  DESIRED_PERMISSIONS(171);

  private final int order;
}