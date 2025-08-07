## Version `v2.0.14` (07.08.2025)
### Changes:
* Remove HTTP priority header to work around DoS CVE-2025-31650 ([MODSIDECAR-130](https://folio-org.atlassian.net/browse/MODSIDECAR-130))

## Version `v2.0.13` (08.07.2025)
### Changes:
* Refresh entitlement info upon failed entitlement check (MODSIDECAR-126)

## Version `v2.0.12` (06.05.2025)
### Changes:
* Problems routing requests to interfaces of type multiple (MODSIDECAR-117)

## Version `v2.0.11` (29.04.2025)
### Changes:
* The AWS Load Balancer closes connections from Sidecars immediately after sending the HTTP response (MODSIDECAR-120)

## Version `v2.0.10` (22.04.2025)
### Changes:
* Support flexible request schema (MODSIDECAR-108)

## Version `v2.0.9` (11.04.2025)
### Changes:
* Fixed port isn't in range issue (MODSIDECAR-108)
* Restrict the Vertex WebClient to use only TLSv1.2 to enable TLS session resumption, as the BouncyCastle library currently does not support this feature for TLSv1.3. (MODSIDECAR-105)
* Simplify the health check response (MODSIDECAR-109)
