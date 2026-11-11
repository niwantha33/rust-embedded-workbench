<!-- SPDX-License-Identifier: Apache-2.0 -->
# ADR 0001 — Independent Rust repository and dependency-free core

Status: Accepted for bootstrap (2026-11-11)

Decision: Develop Rust Embedded Workbench separately from Zephyr Eclipse Workbench. Start with a dependency-free, Java 21 typed catalog and read-only CLI so build, target-selection and provenance rules can be tested before Eclipse/RCP integration.

Rationale: Avoid destabilising the Zephyr release and avoid importing DeviceTree-specific assumptions into native Rust. The tooling ecosystem has multiple paths (Embassy, RTIC, esp-hal, target-specific HALs) which require explicit board and toolchain selection.

Consequences: Features shared with Zephyr (memory, registers, build diagnostics, device data) require explicit adapter contracts. Until validated, the new product makes no Eclipse UI or firmware claims.
