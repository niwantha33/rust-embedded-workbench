<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Embedded Workbench — trusted automatic update activation

## GitHub branches

- [`main`](https://github.com/niwantha33/rust-embedded-workbench/tree/main): source, pull requests, tests and full builds.
- [`updates`](https://github.com/niwantha33/rust-embedded-workbench/tree/updates): **distribution only**, an independent file tree initially containing a `README.md` with an explicit `BOOTSTRAP_ONLY — NO PUBLISHED RELEASE` marker.
- Every qualifying change to `main` triggers the [signed release workflow](../.github/workflows/publish-updates.yml). It builds the real Eclipse p2 site, verifies SHA-512-pinned Eclipse provisioning, checks embedded publisher identity, checks package hashes and only then pushes an immutable release and an Ed25519 signed channel.
- **The `updates` branch is not usable for auto-updating while it has no signed `channel.properties` and `channel.sig`.** A fake or empty manifest must never be treated as a release.

## One-time security provisioning required by the repository owner

**Security-sensitive setup only. Routine workbench users never need Git, Maven, local builds or these steps. Do not send private signing keys to a chatbot, commit them in Git or store them in CI artifacts.** Only the owner or an authorised release administrator should create and manage the publisher signing identity.

1. Securely generate and safeguard a long-lived **Ed25519 signing private key** using a trusted key-management environment. Make a backup under appropriate access controls. Public verification key rotations require an independently authenticated installer update.
2. Commit the corresponding **public key only** to `releng/rew-update-public-key.pem` in standard `-----BEGIN PUBLIC KEY-----` format. Export the same X.509/SPKI public key as base64-encoded DER to `plugins/io.github.niwantha33.rew.ui/keys/publisher-key.b64`. `python3 scripts/verify_signing_public_key.py` checks both public copies correspond and use Ed25519. There is no embedded private key.
3. Open the GitHub repository **Settings → Environments → New environment** and create `rew-update-publisher`. Restrict its deployment to `main`; add environment reviewers and production approval rules where available. Set the environment secret `REW_UPDATE_SIGNING_KEY_PEM` to the private PEM. The workflow does not print or upload the key; its write token is held by the isolated publishing job. **The current connected GitHub integration cannot configure your environment secrets on your behalf.**
4. In **Settings → Rules → Rulesets** (or Branch protection), require PR review and relevant tests on `main`, disable force pushes/deletes to `updates`, and authorise only the controlled publisher identity to write to `updates`. Set GitHub Actions permissions to minimum needed; the workflow's test jobs are read-only. These repository settings require owner access.
5. Test the resulting first-run **Eclipse p2 preview** and the signing-key match. Run/allow **Publish qualified signed Eclipse updates** on `main`. Its first release recognises ONLY the expected empty bootstrap `updates` branch; later releases require verified previous signatures and strictly higher versions. Missing key, corrupted channel or package, failed smoke test, stale source, or non-fast-forward update results in **no publication**. Do not bypass these checks.
6. Before calling it production ready, validate on a disposable Windows Eclipse installation: initial p2 install, signed old→new update, active-debug deferral, trust/licence/restart, offline recovery, tamper/rollback rejection and installed Zephyr compatibility. Record evidence in [capability ledger](CAPABILITY_EVIDENCE.md).

## Critical one-time transition for existing preview users

The screenshot-era preview is **already installed without a pinned publisher key**. That existing binary cannot safely begin trusting a new update channel on its own. Otherwise a malicious replacement channel could choose its own trust key.

Once the owner provisions the key and a qualified signed release, existing preview users need **one manual Eclipse installation** of the trusted, key-bearing p2 feature from a verified GitHub Actions artifact or audited release. Use **Help → Install New Software → Add → Archive**. After restart, **Rust Embedded → Update Settings** can enable signed background checks every six hours (first check about 20 seconds after startup); when a newer signed version is available, the user gets an **Install Update…** option. Eclipse retains its standard licence, trust and restart dialogs. **Updates are checked automatically, not installed silently.**

## On-wire update branch

Once activated, the `updates` branch contains:
- `channel.properties` — publisher-signed release metadata: feature identity, monotonic numeric version, immutable source/artifact commit, exact allowed artifact sizes and SHA-256 hashes
- `channel.sig` — base64 of a detached 64-byte Ed25519 signature over the *exact metadata bytes*
- `releases/<version>/` — immutable Eclipse p2 repository content, including `p2.index`, content/artifacts metadata and `plugins/` and `features/` JARs

The installed plug-in uses fixed GitHub Contents API endpoints, HTTPS only, no redirects, bounded downloads and a pinned publisher verification key. Downloaded files are staged and verified before the Eclipse p2 installer is invoked. The CI workflow cannot sign or publish until the signing identity is supplied through the protected environment.

No vendor USB driver, SDK, Rust toolchain, probe firmware or user firmware project is automatically upgraded by this IDE release channel.
