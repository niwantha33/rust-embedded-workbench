<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Embedded Workbench

**An evidence-first, cross-vendor embedded Rust development workbench.**

An independent open-source project exploring an Eclipse-based IDE for Rust firmware on STM32, TI MSPM0, RP2040, Nordic and Espressif MCUs.

> **Development preview:** an installable Eclipse p2 feature has passed Linux native p2 provisioning in CI. Windows desktop acceptance, Rust language editing, Cargo build integration and firmware debugging are not yet qualified. The public Eclipse p2 update channel uses Eclipse trust prompts; publisher signing remains optional for a future stable channel.

See `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` and `docs/CAPABILITY_EVIDENCE.md` for the implementation plan and qualification requirements.

## Eclipse installation preview (first bootstrap)

**Verified CI build:** [Tycho p2 package + native provisioning smoke](https://github.com/niwantha33/rust-embedded-workbench/actions/runs/43116154729). Open its **Artifacts** section and download `rust-embedded-workbench-eclipse-p2-preview`. The Actions artifact is temporary; a signed stable release channel is still pending.

In Eclipse 2026-09 with Java 21, choose **Help → Install New Software → Add → Archive**, select the downloaded p2 ZIP, install **Rust Embedded Workbench (development preview)** and restart after accepting Eclipse's licence/trust prompts. Open **Rust Embedded → Rust Embedded Perspective** or **Rust Embedded → Board Explorer**.

This is an Eclipse *plug-in feature*, not a standalone Rust editor. Board support is `DOCS_ONLY`; hardware flashing/debugging and signed automatic updates remain unqualified. See [full installation steps](docs/ECLIPSE_INSTALL_PREVIEW.md).

## Setup & Drivers (development preview)

Open **Rust Embedded → Setup & Drivers** to choose your MCU, check installed tools, install approved Rust targets / host utilities and open official probe/USB driver instructions. Use the board explorer's **Setup & Drivers for Selected Board** shortcut for a preselected target. Operations are explicit opt-in; driver replacement, firmware flashing and privilege escalation are **not** performed automatically. See [board-aware setup instructions](docs/SETUP_AND_DRIVERS.md).

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
## Public automatic updates — no custom signing key

The public [updates branch](https://github.com/niwantha33/rust-embedded-workbench/tree/updates) hosts verified, CI-built Eclipse p2 packages. The built-in GitHub Actions token publishes after testing; the installed Eclipse plug-in reads the public URL without an account or token.

Check for updates with **Rust Embedded > Check for Updates**. Automatic checks run after startup and about every six hours. Eclipse requires user approval for untrusted/unsigned artifacts, licence and restart; nothing is silently installed. The currently installed older preview needs one manual update to get this new updater. The update site becomes available only after the first successful publish on main.

This preview has **no custom publisher signature**. Public visibility alone does not guarantee authenticity. See [public p2 security and setup](docs/PUBLIC_P2_UPDATES.md).
