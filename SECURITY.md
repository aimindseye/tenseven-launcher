# Security

Please report a vulnerability, or a request to remove third-party artwork, privately through GitHub:
**Security > Report a vulnerability** on https://github.com/aimindseye/tenseven-launcher (private vulnerability
reporting must be switched on under Settings > Code security). Do not open a public issue for it.

In scope: the launcher's handling of contacts, notifications and installed-app data, anything that could leak
what it reads off the phone, and the build and CI configuration. Out of scope: bugs in Android or in other
apps, and the SableOS operating system (report that to its own repository).

Tenseven has no server, account or network access, so there is no remote attack surface of its own; the
interesting issues are local ones (other apps reading its data, intents it accepts, permissions it holds).
