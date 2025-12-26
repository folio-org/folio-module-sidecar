## Version `v3.0.17` (26.12.2025)
### Changes:
* Fix intermittent 401 Unauthorized errors under load (MODSIDECAR-171)
* 
## Version `v3.0.16` (02.12.2025)
### Changes:
* Consume "System User required" attribute from module bootstrap and request system user token only if the attribute value is true (MODSIDECAR-87)

## Version `v3.0.15` (12.11.2025)
### Changes:
* Bump quarkus version (MODSIDECAR-154)

## Version `v3.0.14` (11.11.2025)
### Changes:
* Fixing Quarkus micro-image build failures (MODSIDECAR-160)

## Version `v3.0.13` (15.10.2025)
### Changes:
* Add support for custom Keycloak base URL for JWKS endpoint, `KC_JWKS_BASE_URL` environment variable (MODSIDECAR-148)
* bump up applications-poc-tools dependencies to 3.0.8

## Version `v3.0.12` (13.10.2025)
### Changes:
* Support FSSP type of Secure Store (APPPOCTOOL-59)
* Add key store configuration properties for secure store (APPPOCTOOL-62)

## Version `v3.0.11` (13.10.2025)
### Changes:
* Register classes for reflection (MODSIDECAR-128)

## Version `v3.0.10` (25.09.2025)
### Changes:
* Bump quarkus-bom from 3.19.2 to 3.20.3 fixing zip bomb ([MODSIDECAR-147](https://folio-org.atlassian.net/browse/MODSIDECAR-147))

## Version `v3.0.9` (17.09.2025)
### Changes:
* Use SECRET_STORE_ENV, not ENV, for secure store key ([MODSIDECAR-140]https://folio-org.atlassian.net/browse/MODSIDECAR-140))
* Fix secure store env variable processing in native sidecar (MODSIDECAR-145)

## Version `v3.0.8` (22.08.2025)
### Changes:
* Native sidecar ECS: Cannot switch affiliation (MODSIDECAR-139)

## Version `v3.0.7` (12.08.2025)
### Changes:
* Server unreachable | Application not enabled (MODSIDECAR-141)
* Add limit query parameter to tenant entitlement request (MODSIDECAR-135)

## Version `v3.0.6` (27.06.2025)
### Changes:
* Remove HTTP priority header to work around DoS CVE-2025-31650 ([MODSIDECAR-130](https://folio-org.atlassian.net/browse/MODSIDECAR-130))

## Version `v3.0.5` (27.06.2025)
### Changes:
* Refresh entitlement info upon failed entitlement check (MODSIDECAR-126)
* Bump application-poc-tools to 3.0.1 version

## Version `v3.0.4` (29.04.2025)
### Changes:
* The AWS Load Balancer closes connections from Sidecars immediately after sending the HTTP response (MODSIDECAR-120)

## Version `v3.0.3` (24.04.2025)
### Changes:
* Support flexible request schema (MODSIDECAR-108)
* Fixed problems routing requests to interfaces of type multiple (MODSIDECAR-117)
* Improve request tracing by adding response time header and Apache-like logging (BF-1070)
