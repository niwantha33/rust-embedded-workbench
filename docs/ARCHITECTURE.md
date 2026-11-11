<!-- SPDX-License-Identifier: Apache-2.0 -->
# Architecture — Rust Embedded Workbench

Status: **design and executable Java CLI bootstrap only**, not an Eclipse product.

## Target architecture

```text
Eclipse RCP / Java UI (planned)
  | Rust editing (Corrosion/LSP4E + rust-analyzer evaluation)
  | MCU & Package Picker | Cargo Profiles | Hardware Studio
  | Debug Perspective | Registers & Memory | RTT/defmt Console
  v
Typed host services (planned)
  | CargoAdapter | RustAnalyzerAdapter | ProbeRsAdapter
  | GdbOpenOcdAdapter | VendorToolchainAdapter
  v
Evidence-first core (bootstrap implemented)
  | BoardRegistry | BuildPlan (preview) | ToolDetector (PATH only)
  v
Versioned vendor catalog + provenanced board metadata
```

## Non-negotiable rules

1. **Do not imply tested hardware support from a catalog entry.** Validation gates are separate: DOCS_ONLY, BUILD_VERIFIED, FLASH_VERIFIED and DEBUG_VERIFIED.
2. **Board identity is explicit:** exact silicon, package, board revision, target triple, HAL features, toolchain, linker layout, probe and debugger backend.
3. **Metadata is not device state:** SVD describes register layouts, not board pin routing; PAC and HAL features are not proof that GPIO assignments are correct.
4. **Never silently execute a build plan.** The current CLI is read-only; future actions need clear controls, log capture, failure handling, cancellation and bounded timeouts.
5. **No universal DeviceTree assumption.** Native Rust frameworks use differing device metadata; Zephyr-Rust integration is a separate backend.
6. **Do not redistribute third-party SDKs or drivers before checking licences.**
7. **No invented measurements:** debug views must identify source and whether each value is observed, derived or unavailable.

## Technical evaluation sequence

- Confirm selected Eclipse release, target platform and Tycho compatibility, then qualify Corrosion and LSP4E with current rust-analyzer.
- Implement Cargo project import and typed process lifecycle first, independent of Eclipse UI widgets.
- Evaluate probe-rs DAP and GDB-server plus vendor-specific GDB/OpenOCD fallback.
- Keep a vendor-neutral description model that references source-backed HAL, PAC, SVD, chip pin multiplexing and board connections.
- Reuse validated concepts from Zephyr Eclipse Workbench, but keep Git histories, releases, IDs and runtime dependencies separate at first.

## Boundaries of current implementation

Implemented: Java records, strict TSV catalog parser, read-only command plan, PATH presence check and regression suite.

Not implemented: Eclipse plugin, Rust language service integration, Cargo runner, firmware build, flashing, debugger, RTT, memory/register views, Hardware Studio, and actual vendor hardware qualification.

See [Capability evidence](CAPABILITY_EVIDENCE.md) and [Hardware qualification](HARDWARE_QUALIFICATION.md).
