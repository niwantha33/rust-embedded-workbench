<!-- SPDX-License-Identifier: Apache-2.0 -->
# Install once; managed, security-gated updates

## The intended user experience

The current installable Eclipse plug-in (not today's CLI prototype) will check for stable updates on start and periodically, show the publisher, installed/new versions and release notes, stage only verified packages, ask before applying, then use Eclipse p2 for license/trust handling, resolution and restart. Users will not need Git, Maven, a command prompt or local source builds for routine updates. There is no silent replacement of running Eclipse or the user's application files.

**Current status (development preview):** the repository now builds an installable Eclipse p2 plug-in feature (Linux p2 smoke passed) with signed update-verification/staging core, update menu, scheduler and p2 installation wizard handoff. The separate [updates distribution branch](https://github.com/niwantha33/rust-embedded-workbench/tree/updates) is initialized but has no signed release; the first-publish bootstrap path has been implemented. There is **no standalone managed product, deployed signing identity, live signed channel or end-to-end validated automatic update**. Checks intentionally fail closed while the public key is absent.

## Security model

1. **Trust anchor:** product embeds an Ed25519 X.509 public key compiled from an owner-controlled offline signing identity. The update channel does not choose its own trusted key. A detached \`channel.sig\` must verify the exact bytes of \`channel.properties\` before any metadata is parsed.
2. **Transport:** fixed \`api.github.com\` HTTPS repository, OS trust store, no redirect following, no token, no arbitrary URL arguments, bounded connect/request times, cancellation and byte caps. Source repository is public.
3. **Manifest:** allowlisted REW feature identity, strictly numeric four-component versions, immutable 40-character lowercase source commit, per-file SHA-256, fixed release folder, p2-only allowed paths, bounded number and total size. The signed channel metadata must be verified before calling the download/staging APIs. Hashes verify bytes; the signature authenticates the signer if the pinned key remains trusted.
4. **Rollbacks:** compare release against running version and highest version previously observed. Reject unknown format, same-version reinstall, downgrade and channel rollback. Persist last successful trusted check in the installed profile; controlled recovery must be explicit.
5. **Staging:** fresh private cache directory, no path traversal, no cache symlink, digest and exact size checked before atomic activation of the verified cache. Failed or cancelled transfers clean up partial files. Do not run any downloaded code from the staging service.
6. **Application:** only the Eclipse p2 engine may apply a verified p2 repository to the *product-owned* installation; Eclipse handles compatibility, licence, publisher trust and restart. It must never update a general user's Eclipse install or quietly rewrite application source, HALs, SDKs, probe firmware, device drivers or toolchains.
7. **Release publisher (CI guard implemented; activation pending installer):** merge to protected \`main\` triggers read-only build/test/CodeQL + native Windows and Linux qualification and packaged p2 verification. A separate short-lived contents-write job must receive the exact verified archive, rehash it, reject stale main, refuse overwriting release directories, require a correctly signed channel, then fast-forward push the \`updates\` branch. A failed gate means no publication.
8. **GitHub settings:** require PR checks, minimal workflow permissions, private vulnerability reporting, secret scanning and push protection if available. Protect \`updates\` from manual/force pushes. Restrict production signing credentials to a reviewed publishing environment. These repository controls require owner verification and are **not** configured by committing documentation.

## Wire format (future publisher)

\`\`\`properties
schema=1
feature=io.github.niwantha33.rew.feature.feature.group
version=0.1.0.20261111220000
revision=<40-lowercase-hex-release-artifact-commit>
directory=releases/0.1.0.20261111220000
notes=Release notes
files=3
file.0.path=p2.index
file.0.size=123
file.0.sha256=<64-lowercase-hex-digest>
file.1.path=content.jar
file.1.size=234
file.1.sha256=<64-lowercase-hex-digest>
file.2.path=artifacts.jar
file.2.size=345
file.2.sha256=<64-lowercase-hex-digest>
\`\`\`

\`channel.sig\` contains a base64-encoded 64-byte Ed25519 signature of the **exact UTF-8 bytes** of \`channel.properties\`. Neither a plain Git commit nor a SHA-256 field is a publisher signature. The signed publisher workflow is committed but its preflight gate intentionally skips publication until a real product, pinned key and provisioning smoke test exist. Key provisioning and GitHub environment restrictions remain the repository owner's responsibilities.

## Separate responsibilities

- **GitHub Actions:** build, test, qualify, sign and publish verified updates after changes merge.
- **Installed app:** check signed metadata, stage verified p2 files, notify and install through p2.
- **User:** install the product once, allow network updates, approve installation/restart when prompted. No local git or script execution for regular updates.
- **Firmware projects:** untouched by IDE updates; toolchain/SDK updates handled separately and with explicit version selection.

See [Capability evidence](CAPABILITY_EVIDENCE.md), [Architecture](ARCHITECTURE.md), and the [security policy](../SECURITY.md).

## Activate the channel

Follow [one-time signing and branch protection setup](ACTIVATE_AUTO_UPDATES.md). The existing unsigned-key-free preview cannot establish trust automatically, so users must install one trusted key-bearing build before signed update checks can operate.

## Key-free public p2 preview (current distribution path)

The current developer preview uses the public updates branch and native Eclipse p2 installation approval. A custom publisher signing key is not needed for that channel. Github Actions publishes qualified p2 bundles using its built-in token. The old signed-channel Java services remain available for future hardened releases but are not a claim that the public preview is publisher-signed.

**Unsigned packages must remain untrusted in Eclipse until the user reviews them.** HTTPS, source control and build tests do not supply the same authentication as a signed executable. The detailed active setup is in [Public p2 updates](PUBLIC_P2_UPDATES.md).
