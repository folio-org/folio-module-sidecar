## Version `v2.0.9` (in progress)
### Changes:
* Fixed port isn't in range issue (MODSIDECAR-108)
* Restrict the Vertex WebClient to use only TLSv1.2 to enable TLS session resumption, as the BouncyCastle library currently does not support this feature for TLSv1.3. (MODSIDECAR-105)
