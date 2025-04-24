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
