# First-run Rust/Cargo setup in Eclipse

The Rust Embedded Workbench provides a guided host-tool installer. It does not require manually typing Cargo or Rustup commands. It also does not install USB drivers, silently alter the system, or promise hardware support.

## In Windows Eclipse

1. Open Rust Embedded > Cargo Workbench. The host status identifies if rustc and Cargo are ready.
2. Click Setup Rust & Cargo... then Verify Rust Tools.
3. Choose Install Rust Essentials... to review all exact package-manager commands. Approve once to run only the displayed commands in order.
4. The background installation shows command output, its exit codes, and cancellation. A failure stops subsequent steps.
5. Click Verify Rust Tools afterwards. Return to Cargo Workbench, click Verify Tools, then Import Existing... to choose a folder containing Cargo.toml. Start with Host > Check.

After explicit confirmation of the full command list, WinGet's source and package agreements are accepted through the displayed flags; the user must review them first. WinGet installs the official Rustlang.Rustup package where missing; Rustup then installs stable Rust/Cargo if unavailable, followed by missing or unverified rust-src, rust-analyzer, Clippy and rustfmt components. On non-Windows systems without Rustup, the installation remains manual via https://rust-lang.org/tools/install/ rather than an unattended downloaded shell script.

After installing, the workbench searches the user's .cargo/bin directly even before Eclipse inherits a refreshed Windows PATH. If Rustup is still not visible, restart Eclipse and Verify Rust Tools again.

## Installed host components

| Component | Command | Purpose |
| --- | --- | --- |
| Rustup (Windows only) | winget install --id Rustlang.Rustup --exact --source winget --accept-source-agreements --accept-package-agreements | Rust toolchain manager and base Rust/Cargo |
| Stable Rust/Cargo | rustup toolchain install stable | Compiler/Cargo when unavailable or broken |
| rust-src | rustup component add rust-src | Standard-library source for language analysis |
| rust-analyzer | rustup component add rust-analyzer | Language-server executable; Eclipse editor integration is not yet implemented |
| Clippy | rustup component add clippy | Cargo lint tool |
| rustfmt | rustup component add rustfmt | Formatting executable; Eclipse editor integration is separate |

The essential installer never installs the board-specific probe-rs/espup/espflash utilities. Select those separately under the board-specific Setup & Drivers table. It never changes USB driver bindings (TI XDS110, ST-LINK, Nordic J-Link, Espressif), probe firmware, Eclipse updates or Zephyr projects.

## Safety and limits

- Every Essentials batch requires one explicit approval displaying every exact command. Existing installed Rust programs are not silently overwritten if the toolchain is managed outside Rustup.
- Installer operations are bounded to 15 minutes per step, cancellable, and fail-closed on nonzero exit. Already completed installations are not rolled back on cancellation.
- Installer subprocesses use fixed argument vectors (no shell pipeline, arbitrary command, forced elevation, or script download).
- WinGet and Rustup can download and execute software, potentially changing the stable compiler version. Review publisher and licence prompts.
- Windows host crate linking may require separate Microsoft Visual Studio C++ Build Tools. The IDE does not silently provision those.
- Compiler/Cargo version checks demonstrate host command availability, not working host linker, MCU compiler/toolchain feature setup, board flash, debugger driver, or rust-analyzer inside Eclipse.
- The Windows desktop first-run installation and any administrator/system prompts still require real-world acceptance testing. CI tests the safe command plans and Eclipse packaging, not actual system installations.

Workbench updates are a distinct Eclipse p2 channel. They require normal Eclipse trust/restart approval.
