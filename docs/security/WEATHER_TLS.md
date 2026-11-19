# Weather TLS boundary and pin rotation

The Launcher contacts only `api.open-meteo.com` and `geocoding-api.open-meteo.com` over HTTPS.
User city text is URI encoded, never used as a host. Approximate coordinates require the
existing Android coarse-location grant. No authorization token, contacts or notification
content is sent. Redirects are rejected, including HTTPS-to-HTTP and cross-host redirects.
The existing five-second connect/read deadlines and cache/stale behavior remain.
Responses are bounded while reading, rather than after unbounded allocation.

Android's normal hostname and system certificate-chain validation remain mandatory.
The network security configuration adds SHA-256 SPKI pins for ISRG Root X1 and Root X2,
computed from the official certificates at https://letsencrypt.org/certificates/ .
Both service chains were independently verified on 2026-11-17: geocoding chains to X1,
forecast to X2 through their YR/YE intermediates. The two roots permit alternate chains
and normal leaf/intermediate renewal. No custom CA, user CA, permissive hostname verifier,
custom TLS socket factory or trust-all implementation is installed.

These are public CA root pins, not leaf certificates or a guarantee that a compromised
pinned CA cannot issue another certificate. They restrict the platform trust set for
these two weather hosts. Other Launcher HTTPS integrations retain system validation.
There is no pin expiry that silently disables enforcement, wildcard domain or debug override.

## Rotation and recovery

Before each distributed Launcher update, verify both service chains on supported Android
versions and compare root SPKI fingerprints to the official CA publication. Before a
provider CA transition, ship an app update containing independently verified new and
current root pins, exercise both chains, then remove retired roots only after adoption.
A third-party provider can change its CA without notice; no vendor rotation commitment is
claimed. That availability risk remains explicit. On a pin mismatch, reject the request,
use only the existing bounded stale cache, then show weather unavailable. Home must keep
working. Never recover by accepting an invalid certificate, HTTP, a new unreviewed root,
remote pin replacement or a switch that disables pin validation. Recovery is a reviewed
Launcher update. The feature remains optional and never blocks startup.

JVM tests prove endpoint, timeout, redirect, response-limit and cleanup behavior; they do
not prove Android's TLS enforcement. Real Android connections must verify both pinned
hosts, and a normally trusted unrelated CA, hostname mismatch and invalid chain must be
rejected. Owner/device acceptance of future provider CA transitions remains required.
