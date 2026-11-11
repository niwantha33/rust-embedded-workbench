<!-- SPDX-License-Identifier: Apache-2.0 -->
# Candidate hardware qualification matrix

This is a planned validation matrix, **not** a list of working boards.

| Priority | Board | Build | Flash | Debug | Probe/backend candidate |
| --- | --- | --- | --- | --- | --- |
| P1 | TI LP-MSPM0G3507 | Not tested | Not tested | Not tested | XDS110 + TI OpenOCD/GDB |
| P1 | STM32 NUCLEO-F446RE | Not tested | Not tested | Not tested | ST-LINK + probe-rs/OpenOCD |
| P1 | Raspberry Pi Pico RP2040 | Not tested | Not tested | Not tested | External CMSIS-DAP/other probe |
| P1 | Nordic nRF52840 DK | Not tested | Not tested | Not tested | J-Link/probe-rs candidate |
| P1 | ESP32-C3 DevKit | Not tested | Not tested | Not tested | espflash/probe-rs/OpenOCD |
| P2 | ESP32-S3 DevKitC | Not tested | Not tested | Not tested | espup + Espressif OpenOCD |

## Required sequence

1. Read official vendor documentation and verify exact board, silicon, package and memory layout.
2. Pin Cargo, Rust, PAC/HAL, linker and optional vendor toolchain versions.
3. Produce a reproducible `no_std` firmware build and record ELF hash/logs.
4. Flash only after positively identifying board and debug probe.
5. Separately validate halt, step, breakpoint, register and memory read, reset and detach.
6. Test a GPIO output and logging transport where supported.
7. Repeat on Windows; annotate different driver/backend behavior.
8. Update the evidence ledger only with recorded results.

**No automatic flashing and no supported-target badges before qualification.**
