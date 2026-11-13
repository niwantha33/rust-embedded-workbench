<!-- SPDX-License-Identifier: Apache-2.0 -->
# Rust Cargo Workbench — first integrated build milestone

## Beginner-friendly two-column view

The Cargo Workbench is divided into two resizable columns. The **left column** is for the project and the action: create/import a project, choose Check/Build/Clippy/Test/Clean/Metadata, choose **This PC (Host — start here)** or a candidate board, optionally choose release/offline, and run the action. The **right column** explains the selected action in simple English, shows Rust/Cargo readiness, and retains the **last Cargo result** separately from the plan.

The long command and full safety preview are collapsed behind **Expert: show exact Cargo command and safety details** on the right. This only changes the presentation: the **confirmation dialog still shows the exact command and safety notice every time**, and cancellation remains visible on the left. You may drag the divider to adjust column widths; both panes have scrollbars for narrow or short Eclipse layouts. A successful host Check does not validate MCU HAL/linker, flash or debug.

## In Eclipse

1. Open **Rust Embedded → Cargo Workbench**, or switch to the **Rust Embedded** perspective and select the **Rust Cargo Workbench** tab.
2. Choose **Import Existing...** and select a directory containing an actual `Cargo.toml`. The import creates a new Eclipse workspace project with a **linked `source/` folder**. It does **not** copy, create, delete or overwrite Rust source files. **Editing or deleting a linked source file in Eclipse affects the original file on disk.**
3. Select the imported Cargo project and a command: **Check**, **Build**, **Clippy**, **Test (HOST ONLY)**, **Clean**, or **Metadata**.
4. Choose **Host (no cross-target)** for your first build, or choose one of the catalog boards. Catalog target triples are candidates, not proof a board's HAL, linker, probe or firmware is ready.
5. Optionally enable **Release profile** or **Offline**. The command preview shows exact arguments. `Cargo.lock` is preserved with `--locked` if present. If a lockfile is missing, Cargo may create one on first build; the confirmation explains that.
6. Select **Run Cargo...**, review the exact command and safety warning, and approve it. Progress appears in the **Rust Embedded — Cargo** Eclipse Console. The task is cancellable through the visible **Cancel Running Job** button and Eclipse's job progress UI.
7. Check the exit code in the Console. File-local rustc short-format errors and warnings appear in **Problems** when they can be mapped to source files in the imported project. Unrecognised messages remain in the Console; no markers are invented.

Cargo uses an absolute resolved executable and fixed argument arrays, not a shell or user-supplied command text. The workbench does not request administrator privileges or run a flash/debug operation. Build scripts, proc macros, Cargo aliases and dependencies may nevertheless run user-provided code in the user's account, so approval is mandatory for each action.

## Targets and restrictions

| Target choice | What runs | What is not claimed |
| --- | --- | --- |
| Host | `cargo check/build` or host tests against the current Rust toolchain | No embedded firmware validation |
| TI MSPM0G3507 | Cargo check/build against candidate `thumbv6m-none-eabi` | No validated Embassy/HAL linker, flash or debug |
| STM32F446RE | Candidate `thumbv7em-none-eabihf` | No validated device/linker/debug |
| RP2040 Pico | Candidate `thumbv6m-none-eabi` | No board-qualified ELF or probe |
| nRF52840 DK | Candidate `thumbv7em-none-eabihf` | No RTT or board debugging |
| ESP32-C3 | Candidate `riscv32imc-unknown-none-elf` | No proven esp-hal/espflash/debug |
| ESP32-S3 | Candidate `xtensa-esp32s3-none-elf`, requires Espressif toolchain | Not supported by standard rustup target alone |

**Test (HOST ONLY)** never runs firmware on the MCU, even when a board is selected. **Metadata** only asks Cargo for local workspace metadata (`--format-version 1 --no-deps`). **Clean** removes Cargo-generated build artifacts and is therefore an explicitly approved action.

## Security and diagnostics limitations

- Cargo output is bounded to 256 KiB per job; output larger than this is truncated. A single unbroken line cannot exhaust unbounded line-buffer memory.
- A command has a 20-minute maximum and honours cancellation; a cancelled or failed job is not reported as successful.
- The Problems view imports only parsable file-local short-format rustc diagnostics, after resolving canonical file paths beneath the current project. External dependency errors and unparsed formats stay in the Console.
- Imported projects link to existing files; they do not create a new Rust manifest or modify existing source. Removing a linked Eclipse resource is different from deleting its contents; use care in Project Explorer.
- Tool versions are not pinned by this feature, and the setup of cross-vendor HAL crates, linker scripts and debuggers is separate. There is no live embedded compile/flash/debug qualification yet.
- The Rust language editor (completion, semantic diagnostics, navigation, formatting) is a separate milestone. [Eclipse Corrosion](https://eclipse-corrosion.github.io/corrosion/) provides an existing rust-analyzer integration candidate, but it has **not yet been qualified** in this combined Zephyr/Rust workbench. Do not silently install it.

## Qualification

Java tests verify command allowlisting, board target selection, consent, host-only tests, Cargo manifest handling, launcher resolution and evidence-only diagnostic parsing. CI also runs actual **dependency-free host** `cargo metadata`, `cargo check`, `cargo build` and `cargo test` commands on Windows and Linux. A separate Tycho job builds and installs the Eclipse p2 preview into a disposable Eclipse Platform. This does not substitute for testing the new view and marker navigation in the user's Windows IDE.

After merge, GitHub Actions publishes the qualified p2 feature to the public [updates site](https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench/updates/site/). Existing users can use **Rust Embedded → Check for Updates** and the native Eclipse update/restart approval flow. The public channel does not claim signed publisher authentication.
