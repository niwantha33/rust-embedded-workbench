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

## R1 — Rust tooling in Eclipse
- [ ] Pinned Tycho/Eclipse RCP minimal product and Windows installation
- [ ] Corrosion / rust-analyzer compatibility spike
- [ ] Rust code completion, diagnostics, navigation and format
- [ ] Cargo import, build, clean, test and profiles with captured logs and cancellation
- [ ] Stable diagnostic service interfaces

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
