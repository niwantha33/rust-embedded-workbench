<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Embedded Workbench

**An evidence-first, cross-vendor embedded Rust development workbench.**

An independent open-source project exploring an Eclipse-based IDE for Rust firmware on STM32, TI MSPM0, RP2040, Nordic and Espressif MCUs.

> **Development bootstrap — not yet a working Eclipse IDE or firmware debugger.** The starter contains a read-only board catalog, build-plan preview, Java CLI and regression tests. Hardware support is not claimed until build, flash and debug have each been validated on real devices.

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