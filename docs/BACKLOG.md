<!-- SPDX-License-Identifier: Apache-2.0 -->
# First implementation tasks

## 1. Pinned Eclipse/Tycho bootstrap
Acceptance: a native RCP window opens from a clean workspace on Windows; tested target platform and product package; no dependency on Zephyr identifiers.

## 2. Corrosion/rust-analyzer compatibility study
Acceptance: import a Rust Cargo project, demonstrate completion, diagnostics and navigation; record exact versions and licences.

## 3. Cargo typed process runner
Acceptance: host build and test from the IDE; command arguments are not shell concatenations; logs/exit codes/cancellation/timeouts verified; target explicit.

## 4. Vendor Board Explorer view
Acceptance: consumes the same catalog as CLI, displays provenance and clearly labels all six as docs-only; rejects invalid data.

## 5. TI LP-MSPM0G3507 firmware qualification
Acceptance: verify Embassy feature `mspm0g3507pm`, linker and package; validate physical flash/debug separately with XDS110/TI OpenOCD fallback.

## 6. ST/RP2040 hardware proof of concept
Acceptance: independently recorded build, flash and debug, including firmware digest and probe versions.

## 7. Probe-rs DAP backend spike
Acceptance: explicit backend, bounded lifecycle, cancellation, logs and no silent flash; mockable integration tests.

## 8. Vendor package/pin metadata model
Acceptance: distinguish SVD, PAC, chip package pinmux and board wiring; no fabricated pin configuration.
