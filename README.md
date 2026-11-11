<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Embedded Workbench

**An evidence-first, cross-vendor embedded Rust development workbench.**

An independent open-source project exploring an Eclipse-based IDE for Rust firmware on STM32, TI MSPM0, RP2040, Nordic and Espressif MCUs.

> **Development bootstrap — not yet an installable Eclipse IDE or firmware debugger.** The source includes a read-only board catalog, Java CLI, signed update verification and p2 file staging. These do not yet constitute a working automatic updater.

See `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` and `docs/CAPABILITY_EVIDENCE.md` for the implementation plan and qualification requirements.

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

The intended production flow matches Zephyr Eclipse Workbench: install the verified product once, let the IDE check signed releases and stage them, then approve Eclipse p2 installation and restart. **Currently there is no Eclipse installer, update scheduler, publisher signing identity or live signed update channel.** See [Secure updates](docs/SECURE_UPDATES.md) and [Security](SECURITY.md).
