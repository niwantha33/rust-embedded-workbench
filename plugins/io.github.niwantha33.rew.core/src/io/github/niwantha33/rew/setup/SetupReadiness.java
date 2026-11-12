// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import io.github.niwantha33.rew.setup.HostToolchain.Report;

/**
 * Evidence-only readiness for a specific catalog step.
 * No executable presence is treated as proof of MCU hardware support.
 */
public final class SetupReadiness {
    private SetupReadiness() {}

    public enum State { INSTALLED, MISSING, UNKNOWN, MANUAL }
    public record Result(State state, String observation, String nextAction,
                         boolean canInstall) {
        public boolean installed() { return state == State.INSTALLED; }
    }

    private static Result installed(String observation) {
        return new Result(State.INSTALLED, observation, "No action needed", false);
    }
    private static Result missing(String observation, boolean canInstall) {
        return new Result(State.MISSING, observation,
                canInstall ? "Review install" : "Open official guide", canInstall);
    }
    private static Result unknown(String observation) {
        return new Result(State.UNKNOWN, observation, "Verify first", false);
    }

    private static Result program(Report report, String tool, boolean prerequisite) {
        if (!report.tools().containsKey(tool))
            return unknown("Not checked yet");
        var observed = report.tools().get(tool);
        return switch (observed.state()) {
            case AVAILABLE -> installed("Installed (version checked)");
            case MISSING -> missing("Not installed", prerequisite);
            case BROKEN -> missing("Found but not working", prerequisite);
        };
    }

    /**
     * Can install only an explicitly observed missing item with available prerequisite.
     * USB driver steps and unknown probes are never automatically installable.
     */
    public static Result evaluate(SetupCatalog.Step step, Report report) {
        if (step.kind() == SetupCatalog.Kind.DRIVER
                || step.mode() == SetupCatalog.Mode.MANUAL)
            return new Result(State.MANUAL, "Hardware/driver not verified",
                    "Open vendor instructions", false);
        if (report == null)
            return unknown("Checking tools...");
        String id = step.id();
        return switch (id) {
            case "install-rustup" -> report.available("rustup")
                    ? installed("Rustup installed — WinGet not needed")
                    : missing("Rustup not working or missing",
                            report.available("winget") && !report.readyForCargo());
            case "install-stable" -> report.readyForCargo()
                    ? installed("Rust compiler and Cargo verified")
                    : missing("Rust compiler or Cargo needs setup", report.available("rustup"));
            case "install-rust-src", "install-clippy", "install-rustfmt",
                    "install-rust-analyzer" -> component(report, id.substring("install-".length()));
            case "install-target" -> report.targetListVerified()
                    ? (report.targetInstalled(step.command().get(3))
                        ? installed("Rust target installed (verified)")
                        : missing("Rust target not installed", report.available("rustup")))
                    : unknown("Rust target list not verified");
            case "install-probe-rs" -> program(report, "probe-rs", report.readyForCargo());
            case "install-espflash" -> program(report, "espflash", report.readyForCargo());
            case "install-espup" -> program(report, "espup", report.readyForCargo());
            case "install-esp-toolchain" -> unknown(
                    "Xtensa compiler not verified; espup presence is not proof");
            default -> unknown("No reliable installed-state check");
        };
    }

    private static Result component(Report report, String component) {
        if (!report.componentListVerified())
            return unknown("Rust component list not verified");
        boolean listed = report.componentInstalled(component);
        if ("rust-analyzer".equals(component) && listed
                && !report.available("rust-analyzer")) {
            return missing("Rust-analyzer component present but command failed",
                    report.available("rustup"));
        }
        return listed ? installed("Installed (Rustup inventory verified)")
                : missing("Component not installed", report.available("rustup"));
    }
}
