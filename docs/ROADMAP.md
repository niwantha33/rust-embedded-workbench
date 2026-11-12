<!-- SPDX-License-Identifier: Apache-2.0 -->
# Milestone roadmap

All milestones are evidence-gated; no schedule or hardware support is promised.

## R0 — foundation
- [x] Independent GitHub repository and Apache-2.0 licence
- [x] Read-only Java board catalog, build-plan preview and tool detection
- [x] Six candidate boards across five MCU vendors
- [x] Local Java 21 verification (starter archive)
- [x] Windows/Linux CI workflow committed
- [ ] Qualify the CI workflow against real GitHub run results

## R0.5 — secure installed-update foundation
- [x] Fixed HTTPS origin, bounded downloads and immutable release manifest verification
- [x] Ed25519 signature verification and typed verified-release API
- [x] Fresh, digest-checked atomic p2 staging and tamper/rollback tests
- [x] Dedicated `updates` branch (bootstrap only; empty until signed publication)
- [x] Automated release publisher workflow with fail-closed product gate, first-release bootstrap and key agreement checks
- [x] Eclipse update menu, periodic Job scheduler and verified p2 wizard handoff (source integration)
- [ ] Provision signer/publisher key and qualify real signed update + restart flow
- [ ] Pinned publisher public key and protected production signing secret
- [ ] Real p2 smoke, Windows desktop update and rollback qualification
- [ ] Validate published site and configure protected updates branch

## R1 — Rust tooling in Eclipse
- [x] Pinned Tycho Eclipse plug-in feature and p2 repository; Linux native p2 install smoke passed
- [ ] Windows Eclipse desktop installation, menu/perspective, uninstall and rollback qualification
- [ ] Standalone managed Eclipse RCP installer/product (beyond plug-in preview)
- [ ] Corrosion / rust-analyzer compatibility spike
- [ ] Rust code completion, diagnostics, navigation and format
- [x] Source-level Cargo project link/import and Check, Build, Clippy, host tests, Clean, Metadata with console and cancellation (CI qualification required)
- [x] Real dependency-free host Cargo smoke test added on Linux/Windows CI (results require verification)
- [x] Evidence-gated short-format rustc diagnostics into Eclipse Problems (GUI acceptance pending)
- [ ] Cross-target embedded build qualifications with linker/HAL feature checks
- [ ] Cargo launch profiles, debugging integration and reproducible target configurations
- [ ] Stable diagnostic service interfaces

## R1.5 — first-run toolchain and drivers

- [x] Vendor-aware Setup & Drivers view, board explorer shortcut and consent-gated tool installation commands (source implemented)
- [x] Unit tests for target triples, command allowlist, consent and driver fail-closed handling
- [ ] Qualify Windows desktop install and installed-tools smoke against Rust/rustup/Cargo/espup/probe-rs
- [x] Read-only Windows PnP USB vendor-ID hint scanner and synthetic tests
- [ ] Real Windows probe scan qualification and driver-state evidence
- [ ] Signed vendor package verification and checked driver installation with explicit consent (no unsafe automatic rebinding)
- [ ] Installer bootstrap for fresh Windows machines and managed tool versions

## R2 — real hardware validation
- [ ] TI LP-MSPM0G3507: Embassy feature, ELF/linker, XDS110/OpenOCD and live debug
- [ ] STM32 NUCLEO-F446RE: build, flash, breakpoint, register inspection
- [ ] RP2040 Pico: build and debug probe validation
- [ ] Nordic nRF52840 DK: build/flash/debug
- [ ] ESP32-C3: RISC-V esp-hal template and flash/debug
- [ ] ESP32-S3: Xtensa espup workflow and backend qualification

## R3 — hardware intelligence
- [ ] Exact MCU/package, pin and peripheral selection
- [ ] Visual hardware graph: PAC ↔ SVD ↔ pin mux ↔ HAL
- [ ] Source-linked register explorer and target memory browser
- [ ] Cargo/HAL feature and linker/profile validation

## R4 — debugger and diagnostics
- [ ] Probe discovery with explicit capability gating
- [ ] DAP and GDB/OpenOCD adapters with lifecycle tests
- [ ] RTT, defmt, UART trace console with drop/overflow indications
- [ ] Framework-aware Embassy/RTIC observability where evidence is available

## R5 — qualified release
- [ ] Windows/Linux hardware-test matrix, documented failures
- [ ] Reproducible versions, dependency/licence review and installer
- [ ] Accurate capability matrix and supported-board badges

## Public p2 release channel without custom keys

- [x] Native Eclipse p2 updater source with fixed public HTTPS URI and user trust approval
- [x] Qualified public p2 publication workflow using ephemeral GITHUB_TOKEN (source)
- [x] First public main-to-updates publication verified; installed Eclipse screenshot confirms matching version
- [ ] Windows desktop upgrade and mandatory unsigned-artifact trust prompt tests
- [ ] Production artifact signatures and authenticated stable release channel (optional hardening)
