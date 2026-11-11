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
| Automatic release publication workflow | Implemented as CI, **inactive until installer/p2 tests/trust key exist** | `.github/workflows/publish-updates.yml`, `publish_update.py` |
| Signed live channel and qualified automatic updates | Not provisioned or end-to-end qualified | Key, signed releases, real Windows UI and update regression tests pending |
| Eclipse Tycho p2 preview feature | Built; Linux native p2 installation passed; Windows UI unverified | [CI run 43116154729](https://github.com/niwantha33/rust-embedded-workbench/actions/runs/43116154729), `pom.xml`, `releng/repository`, `scripts/p2_smoke.py` |
| Board-aware Rust Setup & Drivers view | Source integrated; real Windows UI pending; driver install remains vendor-managed/manual | `SetupCenterView`, `SetupCatalog`, `SetupExecutor`, `SetupTests` |
| Read-only Windows USB VID/PID hints | Source implemented; real Windows PnP behaviour not tested on hardware | `ProbeScanner`, synthetic USB evidence tests |
| Native Rust menu, perspective and candidate Board Explorer | Source implemented; GUI rendering not yet desktop-tested | `plugins/io.github.niwantha33.rew.ui/plugin.xml`, `BoardExplorerView.java`, `RustPerspective.java` |
| Scheduled update checks and Eclipse p2 install-wizard handoff | Source implemented but **fail closed while publisher key/channel are absent** | `UpdateStartup`, `UpdateController`, `UpdateTrust` |
| Rust code editor in Eclipse | Not implemented | None |
| Cargo runner and embedded project generator | Not implemented | None |
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
