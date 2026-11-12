<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Embedded Workbench — Start Here

The workbench has **Guided** (new to Rust) and **Expert** modes. Guided hides unnecessary tool versions and explains the next step. Expert reveals full host status. Both modes keep installation and build approval requirements.

## First program — no hardware needed

1. Open **Rust Embedded → Start Here — Guided Learning...**.
2. Wait for **Check this computer**. **Ready** means Rust and Cargo host commands work. Missing WinGet and espflash are not blockers for the PC Hello World lesson.
3. Choose **Create My First Rust Project...**. Select an existing parent folder and enter a new project name (for example, hello_rust). Review and approve the destination.
4. A new folder is created with Cargo.toml and src/main.rs and linked into Eclipse. Existing folders cannot be overwritten. Editing files through an Eclipse linked project affects the real source.
5. In **Cargo Workbench**, use **Host → Check → Run Cargo...** and approve the exact command. A successful host check does not prove board support or MCU firmware capability. Later try **Build** (Windows linking may require Microsoft C++ Build Tools).
6. Choose a lesson from **Learn at your own pace**. **Read official lesson...** opens the Rust Book, Cargo Book, Embedded Rust Book or probe-rs documentation. These are learning topics, not claims of completed IDE features.

If Rust/Cargo verification fails, use **Setup or repair Rust...**. Installation requires your approval and can be cancelled. Viewing the guided page does not install SDKs or change USB drivers.

## Later: hardware

Select an exact board, then **Show setup for selected board...** to review required targets and vendor drivers. Board catalog entries are documentation candidates only. A target triple, HAL, runtime, linker setup, debugger and connected probe need independent verification before firmware work. No board flashing/debugging is qualified in this preview.

## Expert mode

Select **Expert — show details** to see installed tool versions. The existing Cargo Workbench, board explorer and setup controls remain accessible. Changing modes does not bypass any security or evidence gate.

## Scope and limits

The new host starter project and guided view are initial onboarding features, not a complete interactive course or MCU project generator. Rust editor completion, board-specific firmware templates, automatic flashing/debugging and advanced hardware visualisation still require implementation and real-device evidence. Windows Eclipse GUI acceptance remains pending.

See [Capability evidence](CAPABILITY_EVIDENCE.md) and [Setup & Drivers](SETUP_AND_DRIVERS.md).
