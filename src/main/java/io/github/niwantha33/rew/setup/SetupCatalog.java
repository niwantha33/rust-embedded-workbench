// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.github.niwantha33.rew.core.Board;

/**
 * Fixed, vendor-specific setup recipes. A catalog entry is NOT proof that a
 * board, probe, USB driver or HAL combination has been validated.
 */
public final class SetupCatalog {
    private SetupCatalog() {}

    public enum Mode { MANAGED, MANUAL }
    public enum Kind { TOOLCHAIN, TOOL, DRIVER }
    public record Step(String id, String label, Kind kind, Mode mode,
            List<String> command, String executableCheck, URI documentation,
            String explanation) {
        public Step {
            if (id == null || !id.matches("[a-z0-9-]{2,60}")
                    || label == null || label.isBlank()
                    || documentation == null
                    || !"https".equals(documentation.getScheme())
                    || explanation == null || explanation.isBlank())
                throw new IllegalArgumentException("Invalid setup catalog entry");
            command = List.copyOf(command);
            if (mode == Mode.MANAGED && command.isEmpty())
                throw new IllegalArgumentException("Managed step without a command");
            if (mode == Mode.MANUAL && !command.isEmpty())
                throw new IllegalArgumentException("Manual step cannot execute commands");
        }
        public String displayCommand() {
            return mode == Mode.MANUAL ? "Manual vendor/OS setup" : String.join(" ", command);
        }
    }

    private static URI official(String address) {
        URI uri = URI.create(address);
        if (!"https".equals(uri.getScheme()))
            throw new IllegalArgumentException("Setup documentation must use HTTPS");
        return uri;
    }

    private static Step managed(String id, String label, Kind type,
            List<String> command, String check, String docs, String reason) {
        return new Step(id, label, type, Mode.MANAGED, command, check, official(docs), reason);
    }

    private static Step manual(String id, String label, Kind type,
            String check, String docs, String reason) {
        return new Step(id, label, type, Mode.MANUAL, List.of(), check, official(docs), reason);
    }

    /** Core host setup, distinct from per-board cross-target and USB driver setup. */
    public static List<Step> hostEssentials(boolean windows) {
        List<Step> steps = new ArrayList<>();
        String docs = "https://rust-lang.org/tools/install/";
        if (windows) {
            steps.add(managed("install-rustup", "Install Rustup + Rust/Cargo (WinGet)",
                    Kind.TOOLCHAIN, List.of("winget", "install", "--id",
                            "Rustlang.Rustup", "--exact", "--source", "winget",
                            "--accept-source-agreements", "--accept-package-agreements"),
                    "rustup", docs, "Runs the reviewed Rustlang.Rustup package from WinGet. "
                    + "The reviewed command explicitly accepts the WinGet source and Rustup package "
                    + "agreements after the user confirms the complete plan. Review the "
                    + "publisher and licence first; Windows may still show system prompts. "
                    + "The default Rustup profile normally includes Cargo and Clippy. "
                    + "Windows host linking may also need Microsoft C++ Build Tools."));
        } else {
            steps.add(manual("install-rustup", "Install Rustup using official instructions",
                    Kind.TOOLCHAIN, "rustup", docs,
                    "Follow the official Rustup installer. No remote shell script is "
                    + "automatically downloaded or executed by this workbench."));
        }
        steps.add(managed("install-stable", "Install stable Rust compiler and Cargo",
                Kind.TOOLCHAIN, List.of("rustup", "toolchain", "install", "stable"),
                "rustc", docs, "Installs or updates the stable toolchain with Cargo. "
                + "Run only after reviewing the exact command; this can change your "
                + "existing stable compiler version."));
        steps.add(managed("install-rust-src", "Install Rust standard-library sources",
                Kind.TOOL, List.of("rustup", "component", "add", "rust-src"),
                "", "https://rust-analyzer.github.io/book/installation.html",
                "Installs rust-src, required for complete rust-analyzer language analysis."));
        steps.add(managed("install-rust-analyzer", "Install rust-analyzer language server",
                Kind.TOOL, List.of("rustup", "component", "add", "rust-analyzer"),
                "rust-analyzer", "https://rust-analyzer.github.io/book/installation.html",
                "Installs the rust-analyzer host binary. Full Eclipse LSP integration "
                + "is a separate feature and is not yet claimed as working."));
        steps.add(managed("install-clippy", "Install Clippy lint tool",
                Kind.TOOL, List.of("rustup", "component", "add", "clippy"),
                "", "https://doc.rust-lang.org/clippy/",
                "Installs Rust's Clippy diagnostics for Cargo Workbench."));
        steps.add(managed("install-rustfmt", "Install rustfmt formatter",
                Kind.TOOL, List.of("rustup", "component", "add", "rustfmt"),
                "", "https://github.com/rust-lang/rustfmt",
                "Installs rustfmt; automatic Eclipse editor integration is not yet available."));
        return List.copyOf(steps);
    }

    public static List<Step> forBoard(Board board, boolean windows) {
        var steps = new ArrayList<Step>(hostEssentials(windows));
        String rust = "https://rust-lang.org/tools/install/";
        if (board.toolchain() == Board.Toolchain.stable) {
            steps.add(managed("install-target", "Install Rust target: " + board.rustTarget(),
                    Kind.TOOLCHAIN, List.of("rustup", "target", "add", board.rustTarget()),
                    "", rust, "Uses rustup's official target distribution; does not compile or flash firmware."));
        } else {
            steps.add(managed("install-espup", "Install Espressif toolchain installer (espup)",
                    Kind.TOOLCHAIN, List.of("cargo", "install", "espup", "--locked"),
                    "espup", "https://docs.espressif.com/projects/rust/book/getting-started/toolchain.html",
                    "Cargo compiles a third-party package and may run build scripts. Review its source "
                    + "and confirm before installing. Required for ESP32-S3 Xtensa."));
            steps.add(managed("install-esp-toolchain", "Install Espressif Xtensa Rust toolchain",
                    Kind.TOOLCHAIN, List.of("espup", "install"), "",
                    "https://docs.espressif.com/projects/rust/book/getting-started/toolchain.html",
                    "Runs the official Espressif setup utility; downloads a separate Xtensa "
                    + "compiler and dependencies. Do not use rustup target add for Xtensa."));
        }
        if (board.id().startsWith("esp32")) {
            steps.add(managed("install-espflash", "Install Espressif flash utility",
                    Kind.TOOL, List.of("cargo", "install", "espflash", "--locked"),
                    "espflash", "https://docs.espressif.com/projects/rust/book/",
                    "Installs host software only. It does not flash any connected board."));
            steps.add(manual("usb-bridge-driver", "Identify USB/UART bridge and Windows driver",
                    Kind.DRIVER, "",
                    "https://github.com/espressif/esp-idf/blob/master/docs/en/get-started/establish-serial-connection.rst",
                    "Windows often installs the driver automatically. USB-UART bridges vary "
                    + "(CP210x, FTDI, etc.). Inspect the actual board and Windows Device Manager "
                    + "before choosing a signed vendor driver; native USB-JTAG may need none."));
        } else if (board.id().equals("mspm0g3507-launchpad")) {
            steps.add(manual("ti-xds110", "TI XDS110 probe driver / TI OpenOCD",
                    Kind.DRIVER, "openocd",
                    "https://software-dl.ti.com/ccs/esd/documents/xdsdebugprobes/emu_xds110.html",
                    "Use TI's XDS110 drivers and firmware matched to the LaunchPad. "
                    + "Do not replace the XDS110 driver with WinUSB without a documented reason. "
                    + "probe-rs support for this exact board/probe is not qualified."));
        } else {
            steps.add(managed("install-probe-rs", "Install probe-rs debugger tools",
                    Kind.TOOL, List.of("cargo", "install", "probe-rs-tools", "--locked"),
                    "probe-rs", "https://probe.rs/docs/getting-started/installation/",
                    "Cargo may build third-party code. Installs host-side tools only; "
                    + "does not configure USB drivers, flash or reset hardware."));
            if (board.id().equals("stm32f446re-nucleo")) {
                steps.add(manual("stm32-stlink-driver", "ST-LINK official USB driver",
                        Kind.DRIVER, "",
                        "https://www.st.com/en/development-tools/stsw-link009.html",
                        "For Windows use the signed STSW-LINK009 driver. "
                        + "Check the exact ST-LINK interface before changing drivers."));
            } else if (board.id().equals("nrf52840-dk")) {
                steps.add(manual("nrf-jlink-driver", "SEGGER J-Link driver / WinUSB compatibility",
                        Kind.DRIVER, "",
                        "https://probe.rs/docs/getting-started/probe-setup/",
                        "J-Link USB driver selection depends on the debugger backend. "
                        + "Switching to WinUSB may affect SEGGER tooling; do not automate replacement."));
            } else if (board.id().equals("rp2040-pico")) {
                steps.add(manual("pico-cmsis-dap", "Raspberry Pi Debug Probe (CMSIS-DAP)",
                        Kind.DRIVER, "",
                        "https://www.raspberrypi.com/documentation/microcontrollers/debug-probe.html",
                        "Pico needs a separate SWD probe for debugging. A CMSIS-DAP probe "
                        + "usually uses the OS driver; verify the actual connected hardware."));
            }
        }
        return List.copyOf(steps);
    }

    public static Step require(List<Step> plan, String stepId) {
        return plan.stream().filter(s -> s.id().equals(stepId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown setup step"));
    }
}
