<!-- SPDX-License-Identifier: Apache-2.0 -->
# Capability evidence ledger

Only this file should make positive implemented-capability claims. Planned features elsewhere are not proof of availability.

| Feature | Status | Evidence |
| --- | --- | --- |
| Strict board catalog parser | Implemented; starter locally tested on Linux JDK 21 | `BoardRegistry`, `BootstrapTests`, `scripts/verify.sh` |
| Board list and details CLI | Implemented; starter locally tested | `Main boards`, `Main show` |
| Explicit target/build plan | Implemented; **preview only** | `BuildPlan`; never runs commands |
| Tool PATH presence check | Implemented; not version or usability validation | `ToolDetector`, `Main doctor` |
| GitHub Windows/Linux CI | Implemented; check the exact PR run before merge | `.github/workflows/verify.yml` |
| Signed update manifest verification | Implemented in Java core; no product integration yet | `SignedChannel`, `VerifiedRelease`, tests |
| Bounded HTTPS channel | Implemented; live channel not provisioned | `BoundedUpdateHttp`, `GitHubUpdateChannel` |
| Verified p2 staging | Implemented; not an installer | `VerifiedUpdateStager` and fault tests |
| p2 publisher package validation | Implemented offline, not yet automated publication | `rew_update_site.py`, tests |
| Dedicated `updates` distribution branch | First public p2 publication verified (version 0.1.0.20261112001031) | [updates branch](https://github.com/niwantha33/rust-embedded-workbench/tree/updates) |
| Public Eclipse p2 publisher pipeline | Implemented; first verified published release, ongoing qualification required | `.github/workflows/publish-updates.yml`, `publish_public_p2.py` |
| Key-free public p2 update UI | Implemented in Java source; **unsigned content requires explicit Eclipse p2 trust approval**, Windows desktop validation pending | `PublicP2Updates.java`, `UpdateStartup.java`, p2 CI |
| Publisher-authenticated signed stable channel | Optional separate hardening track, not activated | Legacy Ed25519 core, no live signed publisher |
| Eclipse Tycho p2 preview feature | Built; Linux native p2 installation passed; Windows UI unverified | [CI run 43116154729](https://github.com/niwantha33/rust-embedded-workbench/actions/runs/43116154729), `pom.xml`, `releng/repository`, `scripts/p2_smoke.py` |
| Compact PC setup + optional board-driver guidance | Source integrated: host-ready summary and Start Here continuation, expert table collapsible, human-readable installed component names, board-specific driver checklist and read-only USB hints in hardware section. No real-device qualification; Windows native UI acceptance pending | SetupCenterView, SetupReadiness, test_first_run_setup.py, Eclipse p2 CI |
| Evidence-aware Setup & Drivers readiness | Source integrated: verified host version/component/target results mapped per recipe, initial board unselected, non-installed states distinct, install disabled for known installed steps; Windows GUI acceptance pending | HostToolchain, SetupReadiness, SetupReadinessTests, SetupCenterView, test_first_run_setup.py |
| Board-aware Rust Setup & Drivers view | Source integrated; real Windows UI pending; driver install remains vendor-managed/manual | `SetupCenterView`, `SetupCatalog`, `SetupExecutor`, `SetupTests` |
| Read-only Windows USB VID/PID hints | Source implemented; real Windows PnP behaviour not tested on hardware | `ProbeScanner`, synthetic USB evidence tests |
| Native Rust menu, perspective and candidate Board Explorer | Source implemented; GUI rendering not yet desktop-tested | `plugins/io.github.niwantha33.rew.ui/plugin.xml`, `BoardExplorerView.java`, `RustPerspective.java` |
| Scheduled public p2 checks and Eclipse update wizard | Windows screenshot verified read of initial published version; later update discovery experienced a stale-cache bug. Immutable release metadata/refresh fix implemented in source; Windows upgrade acceptance pending | `UpdateStartup`, `PublicP2Updates`, `PublicP2Channel`, cache regression tests |
| Rust code editor in Eclipse | Not implemented | None |
| Cargo Workbench two-column beginner layout | Source integrated: adjustable left project/action pane, right plain-language guidance and last execution result, optional expert command preview, original safety confirmation and cancellation retained; real Windows desktop screenshot acceptance pending | CargoWorkbenchView, test_guided_onboarding.py, Eclipse Tycho p2 CI |
| Cargo runner, project import and evidence-based diagnostics | Source integrated: typed plans, linked Eclipse project, bounded host process, Console and Problems. Cross-OS real Cargo smoke/GUI acceptance pending | `CargoPlan`, `CargoExecutor`, `CargoWorkbenchView`, `CargoProblems`, `CargoDiagnostics`, CI tests |
| Guided Start Here view (guided/expert modes) | Source integrated: bounded Rust host checks, mode preference, curated official learning links and board setup navigation. Native Windows GUI acceptance pending | StartHereView, OpenStartHereHandler, plugin.xml, test_guided_onboarding.py |
| Safe host-only Hello World project generator | Source integrated: creates new Cargo.toml and src/main.rs only in a new directory; overwrite/reuse prevented. Native Windows GUI acceptance pending | StarterProject, StarterProjectTests, CargoWorkbenchView |
| Embedded Rust project generator | Not implemented | None |
| Flash/debug on any listed board | Not implemented or validated | None |
| SVD, pin GUI, RTOS views or memory browser | Not implemented | None |

## Promotion criteria

A board must not progress from DOCS_ONLY merely because a HAL supports the chip family. BUILD_VERIFIED needs a versioned build transcript and artifact; FLASH_VERIFIED additionally needs live device and probe evidence; DEBUG_VERIFIED requires verified stepping, registers, memory and halt/reset behavior. Record exact silicon package, board and probe revisions, OS, toolchain/HAL versions, linker setup, target triple, commit SHA, UTC time, artifact digest and logs.

## Hardware evidence template

```yaml
board_id: mspm0g3507-launchpad
revision: <commit-sha>
tested_at_utc: <iso-8601>
os: <os-version>
mcu_package: <exact-part-number>
board_revision: <revision>
rustc: <version>
cargo: <version>
hal: <crate-version>
debug_backend: <probe-rs-or-openocd-version>
probe_model: <model>
probe_firmware: <version>
test: <build|flash|debug>
result: <pass|fail|not-tested>
artifact_sha256: <hash>
log_path: <relative-path>
```

| Guided Rust host installer | Source implemented: per-tool version checks, one approved WinGet/Rustup batch, cancelled/failure stop; physical Windows installation still unverified | HostToolchain, SetupCatalog, SetupExecutor, SetupCenterView, HostToolchainTests |
| Cargo toolchain readiness banner | Source implemented; Run Cargo gated until rustc and Cargo version probes pass; Windows UI acceptance pending | CargoWorkbenchView, test_first_run_setup.py |
