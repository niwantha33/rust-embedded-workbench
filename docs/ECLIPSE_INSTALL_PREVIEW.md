<!-- SPDX-License-Identifier: Apache-2.0 -->
# First Eclipse plug-in preview — installation and qualification

**Status (2026-11-11):** a preview p2 site was built and installed into a disposable Eclipse copy in [successful GitHub Actions run 43116154729](https://github.com/niwantha33/rust-embedded-workbench/actions/runs/43116154729). This verifies native p2 provisioning on Linux, **not** the actual Windows desktop experience or automatic signed updates. Source archives are NOT Eclipse installation packages.

## Intended installation from a verified p2 archive

1. Open the [successful Eclipse p2 CI workflow](https://github.com/niwantha33/rust-embedded-workbench/actions/runs/43116154729), or the latest successful run from a verified commit. The **Artifacts** area contains a downloadable preview ZIP (GitHub login may be required; artifacts expire).
2. Download the `rust-embedded-workbench-eclipse-p2-preview` artifact. Inspect that its ZIP root contains `p2.index`, `content.jar` or `content.xml`, `artifacts.jar` or `artifacts.xml`, and `plugins/` and `features/`.
3. Open a compatible Eclipse IDE with Java 21 on Windows and select **Help → Install New Software → Add → Archive**, choosing the p2 ZIP, **not** GitHub's Source Code ZIP.
4. Select **Rust Embedded Workbench (development preview)**, review the declared license and Eclipse trust dialogs, then restart when Eclipse requests it.
5. Select **Window → Perspective → Open Perspective → Other → Rust Embedded**, or the **Rust Embedded** top-level menu → **Rust Embedded Perspective**.
6. Select **Rust Embedded → Board Explorer**. The six boards must all show `DOCS_ONLY` qualification. The filter and details view must work.
7. Select **Rust Embedded → Check for Updates**. After replacing the older preview with this release, public p2 checking works without a custom key when the updates site is published. Eclipse asks for confirmation and trust before any untrusted/unsigned package is installed. When production signing is provisioned, this first preview must be manually replaced with a trusted key-bearing p2 feature once, because it cannot safely bootstrap trust from a remote branch.
8. Open **Rust Embedded → Setup & Drivers** or select a board and click **Setup & Drivers for Selected Board**. Review the exact target and commands. Use **Official Instructions** for Windows drivers; driver changes require vendor/system UI approval.
9. Select **Rust Embedded → Update Settings** to change the periodic check preference. Without the pinned signing key, checking remains fail-closed even if the preference is enabled.

## What is deliberately not claimed

- Not a complete Rust code editor, Cargo project wizard, flashing or debugging environment yet.
- Not a private standalone Eclipse installation; this is a feature installed into a compatible existing Eclipse IDE.
- Not a working public signed update channel until the publisher key, p2 smoke test and release approvals exist.
- Not guaranteed to coexist with every Eclipse/Zephyr installation until tested on clean and dual-plugin profiles.
- Updates to application projects, SDKs, vendor drivers, Cargo.lock and toolchains are not part of IDE updates.

## Acceptance evidence required

- Automated: **PASSED in run 43116154729** — exact-feature Tycho build, site verification, SHA-512 pinned Eclipse Platform download and native p2 installation into a disposable copy.
- Manual/desktop: clean Eclipse 2026-09 Windows installation, menu/perspective/view rendering, restart and uninstall.
- Later: old→new signed p2 upgrade and rollback, offline/tampered/signature mismatch failure, active build/debug postponement.

Inspect `docs/CAPABILITY_EVIDENCE.md` for what has actually passed.

Public key-free update-channel details: [Public p2 updates](PUBLIC_P2_UPDATES.md). A public site is not publisher-authenticated merely because it uses HTTPS.
