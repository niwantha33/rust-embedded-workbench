<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Embedded Setup & Drivers (development preview)

## First-run workflow

Open **Rust Embedded → Setup & Drivers**, or select a board in **Rust Board Explorer** and choose **Setup & Drivers for Selected Board**.

1. Choose the exact target board. All entries remain **DOCS_ONLY**; this is setup assistance, not hardware qualification.
2. Use **Refresh Setup Status** to check whether a relevant tool name appears on the current process `PATH`. A positive result **does not** prove the correct version or working hardware. Restart Eclipse after installing host tools to refresh `PATH`.
3. Select an **Install Selected...** step. The workbench shows the exact approved command and warns if it may download/build third-party code. **Only** after confirmation does it run the fixed argument vector in a cancellable background Eclipse Job. Failures and nonzero exits are reported, not hidden.
4. Select a **DRIVER** step and click **Official Instructions**. On Windows inspect the actual USB device and its driver in Device Manager. Use the vendor's signed driver installer where appropriate. This preview does **not** install or replace kernel/USB drivers, run Zadig, update probe firmware, grant admin permissions, or change a device's driver binding.
5. After installing dependencies, review the detected versions, actual debugger/probe and a real build/debug test. A tool merely appearing on `PATH` is not qualification.

The setup process never uses `cmd /c`, `sh -c`, `curl | sh`, `iex`, unauthenticated downloads, arbitrary command arguments or privilege escalation. Package installations use the current user's `rustup`, `cargo`, `espup` or (Windows) the exact `Rustlang.Rustup` ID from the Microsoft Windows Package Manager catalog. Package managers may download and run code from their distribution sources, so each install requires explicit user approval. The workbench does not pin external installer packages yet; versions must be verified before claiming reproducibility.

## Target-specific driver guidance

| Target | Rust/toolchain setup | Probe and Windows USB driver guidance |
| --- | --- | --- |
| TI LP-MSPM0G3507 | Official rustup; `thumbv6m-none-eabi` | TI XDS110 driver and compatible TI OpenOCD; verify the TI debugger interface. **Do not silently replace TI's driver with WinUSB.** [XDS110 manual](https://software-dl.ti.com/ccs/esd/documents/xdsdebugprobes/emu_xds110.html) |
| STM32 NUCLEO-F446RE | Official rustup; `thumbv7em-none-eabihf` | ST-LINK; for Windows use ST's signed [STSW-LINK009](https://www.st.com/en/development-tools/stsw-link009.html), if needed |
| RP2040 Pico | Official rustup; `thumbv6m-none-eabi` | External SWD debug probe, e.g. [Raspberry Pi Debug Probe](https://www.raspberrypi.com/documentation/microcontrollers/debug-probe.html) with CMSIS-DAP |
| Nordic nRF52840 DK | Official rustup; `thumbv7em-none-eabihf` | J-Link. Review [probe-rs guidance](https://probe.rs/docs/getting-started/probe-setup/) before selecting a WinUSB driver; this can change SEGGER compatibility |
| ESP32-C3 | Official stable Rust; `riscv32imc-unknown-none-elf`; optionally espflash | Native USB-JTAG may be driverless; the USB-UART bridge chip varies by board. Check [Espressif's serial guidance](https://github.com/espressif/esp-idf/blob/master/docs/en/get-started/establish-serial-connection.rst) |
| ESP32-S3 | Rust + `espup` + Xtensa toolchain (not `rustup target add`) | Native USB-JTAG vs USB-UART depends on the board/port. Consult [Espressif's Rust toolchain guide](https://docs.espressif.com/projects/rust/book/getting-started/toolchain.html) |

For devices using WinUSB, **changing the USB driver can disable the official vendor debugger**. Never apply a generic driver without knowing the exact USB interface and debugger backend. Drivers, probe firmware and licence-restricted vendor tools are never silently installed, redistributed or updated.

## Supported actions vs limits

Implemented in source: catalog-owned board plans, fixed managed tool-install recipes, approval prompts, bounded/cancellable host process execution, readable output, official vendor documentation links, and board explorer navigation.

Not implemented or qualified: unattended system-driver installation, Windows PnP driver detection or driver-signature verification, automatic OS or SDK provisioning, precise installed tool versions, ESP-IDF toolchain verification, real probe health checks, or full Windows GUI acceptance. There is no `rust-analyzer` editor integration or Cargo workspace integration yet.

This setup assistant runs **inside the Eclipse plug-in preview**; it is not a standalone application installer. Installation of the Eclipse feature itself still uses the p2 ZIP described in [Eclipse install preview](ECLIPSE_INSTALL_PREVIEW.md). IDE automatic updates remain disabled until the signing key and publisher are provisioned.
