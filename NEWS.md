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

## Version `v3.0.2` (11.04.2025)
### Changes:
* Restrict the Vertex WebClient to use only TLSv1.2 to enable TLS session resumption, as the BouncyCastle library currently does not support this feature for TLSv1.3. (MODSIDECAR-105)

## Version `v3.0.1` (07.04.2025)
### Changes:
* Fix port is not in the range issue (MODSIDECAR-108)
* Simplify the health check response (MODSIDECAR-109)
* Review previously disabled tests, make them work or remove (MODSIDECAR-107)
