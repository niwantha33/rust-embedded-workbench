<!-- SPDX-License-Identifier: Apache-2.0 -->
# Security policy

Rust Embedded Workbench is an experimental development bootstrap. **No released installer or firmware debugger is qualified for production use.** Firmware builds, Cargo scripts and vendor SDKs can execute code with the user's permissions once integrated.

## Private reporting

Do not post credentials, private firmware, exploit details or sensitive logs publicly. Use **GitHub Security → Advisories → Report a vulnerability** if enabled. Otherwise open a non-sensitive request for a private contact channel. No guaranteed SLA is claimed.

## Secure operation

- Use least-privilege accounts for Cargo, SDKs, firmware builds and debugger operations; do not run arbitrary projects as administrator.
- Never flash or reset a target until exact hardware and probe identity have been verified and the user has approved the action.
- User projects, external SDKs, HAL crates, Cargo.lock, device drivers and probe firmware must not be silently overwritten by IDE updates.
- Treat external SVD/PAC packages and vendor metadata as untrusted until pinned and validated; SVD describes register layouts, not physical wiring.
- Never put credentials in source, project files, URLs or logs. The public GitHub update channel requires no user token; any private integration should use Eclipse secure storage.

## Signed workbench updates

The implemented core enforces a fixed HTTPS GitHub origin, no redirects, bounded downloads, an allowlisted p2 feature, immutable commit references, SHA-256 checks, detached Ed25519 publisher signature verification and atomic staging. SHA-256 alone is not publisher identity. The public key must be pinned in the installed binary, not selected by a remote channel.

**Not yet live:** the Eclipse RCP installer, background scheduler, p2 UI, signing-key provisioning and remote signed update channel. See [the secure-updates plan](docs/SECURE_UPDATES.md). Never disable trust checks to work around a failed update.

## Repository security

CI uses read-only permissions for tests; CodeQL runs separately. Production publishing must isolate write credentials to its final push after exact artifact verification and signing. Branch rules, required reviews, secret scanning, push protection, Dependabot and environment restrictions require independent GitHub owner verification; documentation alone does not configure them.

Suspected update-channel compromise: stop distribution, preserve evidence, rotate compromised signing material through a separately authenticated trust migration and publish an advisory.

## Public p2 preview channel

The development preview can use the fixed HTTPS public Eclipse p2 repository without any custom signing secret. GitHub Actions uses its own scoped GITHUB_TOKEN for publication. Eclipse p2 trust and unsigned-artifact warnings must remain enabled; approval is required before software installation. The legacy Ed25519-verifying core remains in the tree as an optional hardened design, but the public preview does **not** claim signed publisher authentication.

See docs/PUBLIC_P2_UPDATES.md. Restrict write permissions to updates, protect main and review artifact provenance before promoting any public preview to stable.
