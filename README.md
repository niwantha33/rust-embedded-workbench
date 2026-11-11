<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Embedded Workbench

**An evidence-first, cross-vendor embedded Rust development workbench.**

An independent open-source project exploring an Eclipse-based IDE for Rust firmware on STM32, TI MSPM0, RP2040, Nordic and Espressif MCUs.

> **Development preview:** an installable Eclipse p2 feature has passed Linux native p2 provisioning in CI. Windows desktop acceptance, Rust language editing, Cargo build integration and firmware debugging are not yet qualified. The signed automatic update channel is not yet activated.

See `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` and `docs/CAPABILITY_EVIDENCE.md` for the implementation plan and qualification requirements.

## Eclipse installation preview (first bootstrap)

**Verified CI build:** [Tycho p2 package + native provisioning smoke](https://github.com/niwantha33/rust-embedded-workbench/actions/runs/43116154729). Open its **Artifacts** section and download `rust-embedded-workbench-eclipse-p2-preview`. The Actions artifact is temporary; a signed stable release channel is still pending.

In Eclipse 2026-09 with Java 21, choose **Help → Install New Software → Add → Archive**, select the downloaded p2 ZIP, install **Rust Embedded Workbench (development preview)** and restart after accepting Eclipse's licence/trust prompts. Open **Rust Embedded → Rust Embedded Perspective** or **Rust Embedded → Board Explorer**.

This is an Eclipse *plug-in feature*, not a standalone Rust editor. Board support is `DOCS_ONLY`; hardware flashing/debugging and signed automatic updates remain unqualified. See [full installation steps](docs/ECLIPSE_INSTALL_PREVIEW.md).

## Verify

With JDK 21:

```bash
bash scripts/verify.sh
java -cp .build/main io.github.niwantha33.rew.cli.Main boards
java -cp .build/main io.github.niwantha33.rew.cli.Main plan mspm0g3507-launchpad
```

Windows: `./scripts/verify.ps1` using PowerShell.

The project is separate from [Zephyr Eclipse Workbench](https://github.com/niwantha33/zephyr-eclipse-workbench), although proven ideas may be reused later after qualification.

Apache-2.0 licence.
## Install-once managed updates

The intended production flow matches Zephyr Eclipse Workbench: install the verified product once, let the IDE check signed releases and stage them, then approve Eclipse p2 installation and restart. **A p2 plug-in preview exists**, with update menus, a scheduler and a native Eclipse p2 install-wizard handoff. The publisher signing identity and live signed update channel are **not provisioned**; until they are, checking is disabled rather than allowing unsigned updates. See [Secure updates](docs/SECURE_UPDATES.md) and [Security](SECURITY.md).
