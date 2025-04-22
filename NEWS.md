## Version `v2.0.11` (in progress)
### Changes:

## Version `v2.0.10` (22.04.2025)
### Changes:
* Support flexible request schema (MODSIDECAR-108)

## Version `v2.0.9` (11.04.2025)
### Changes:
* Fixed port isn't in range issue (MODSIDECAR-108)
* Restrict the Vertex WebClient to use only TLSv1.2 to enable TLS session resumption, as the BouncyCastle library currently does not support this feature for TLSv1.3. (MODSIDECAR-105)
* Simplify the health check response (MODSIDECAR-109)
