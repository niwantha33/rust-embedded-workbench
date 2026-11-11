<!-- SPDX-License-Identifier: Apache-2.0 -->
# First Eclipse plug-in preview — installation and qualification

**Status:** Implementation in progress. A local Java CLI or GitHub source archive is NOT an Eclipse installation package. A real release requires a successful Tycho package, p2 repository validation and native install/launch testing.

## Intended installation from a verified p2 archive

1. In GitHub Actions, open a successful **Build Rust Workbench Eclipse p2 preview** workflow run (or a future verified GitHub Release).
2. Download the `rust-embedded-workbench-eclipse-p2-preview` artifact. Inspect that its ZIP root contains `p2.index`, `content.jar` or `content.xml`, `artifacts.jar` or `artifacts.xml`, and `plugins/` and `features/`.
3. Open a compatible Eclipse IDE with Java 21 on Windows and select **Help → Install New Software → Add → Archive**, choosing the p2 ZIP, **not** GitHub's Source Code ZIP.
4. Select **Rust Embedded Workbench (development preview)**, review the declared license and Eclipse trust dialogs, then restart when Eclipse requests it.
5. Select **Window → Perspective → Open Perspective → Other → Rust Embedded**, or the **Rust Embedded** top-level menu → **Rust Embedded Perspective**.
6. Select **Rust Embedded → Board Explorer**. The six boards must all show `DOCS_ONLY` qualification. The filter and details view must work.
7. Select **Rust Embedded → Check for Updates**. Until signing is provisioned, expect the explicit **not activated** message; an unsigned update must never be installed.
8. Select **Rust Embedded → Update Settings** to change the periodic check preference. Without the pinned signing key, checking remains fail-closed even if the preference is enabled.

## What is deliberately not claimed

- Not a complete Rust code editor, Cargo project wizard, flashing or debugging environment yet.
- Not a private standalone Eclipse installation; this is a feature installed into a compatible existing Eclipse IDE.
- Not a working public signed update channel until the publisher key, p2 smoke test and release approvals exist.
- Not guaranteed to coexist with every Eclipse/Zephyr installation until tested on clean and dual-plugin profiles.
- Updates to application projects, SDKs, vendor drivers, Cargo.lock and toolchains are not part of IDE updates.

## Acceptance evidence required

- Automated: exact-feature Tycho build, archive structure/digest, read-only CI and Java/catalog tests.
- Manual/desktop: clean Eclipse 2026-09 Windows installation, menu/perspective/view rendering, restart and uninstall.
- Later: old→new signed p2 upgrade and rollback, offline/tampered/signature mismatch failure, active build/debug postponement.

Inspect `docs/CAPABILITY_EVIDENCE.md` for what has actually passed.
