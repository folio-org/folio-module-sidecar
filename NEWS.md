## Version `v3.1.0` (in progress)
### Changes:
* Fixed port isn't in range issue (MODSIDECAR-108)
* Restrict the Vertex WebClient to use only TLSv1.2 to enable TLS session resumption, as the BouncyCastle library currently does not support this feature for TLSv1.3. (MODSIDECAR-105)
* Support flexible request schema (MODSIDECAR-108)
* Fixed problems routing requests to interfaces of type multiple (MODSIDECAR-117)
* Improve request tracing by adding response time header and Apache-like logging (BF-1070)
* Refresh entitlement info upon failed entitlement check (MODSIDECAR-126)