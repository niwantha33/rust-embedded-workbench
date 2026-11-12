// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import io.github.niwantha33.rew.core.BoardRegistry;

public final class SetupReadinessTests {
    private static int checks;
    private static void ok(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }
    private static HostToolchain.Report report(boolean toolsReady, boolean winget,
            boolean targetsVerified, Set<String> targets, boolean componentsVerified) {
        Map<String, HostToolchain.Tool> found = new HashMap<>();
        for (String name : new String[] {"rustup", "rustc", "cargo",
                "rust-analyzer", "probe-rs", "espflash", "winget", "espup"}) {
            boolean available = switch (name) {
                case "winget" -> winget;
                case "espflash", "espup" -> false;
                default -> toolsReady;
            };
            found.put(name, new HostToolchain.Tool(name,
                    available ? HostToolchain.State.AVAILABLE : HostToolchain.State.MISSING,
                    null, available ? name + " test version" : ""));
        }
        return new HostToolchain.Report(found,
                Set.of("rust-src", "rust-analyzer", "clippy", "rustfmt"),
                componentsVerified, targets, targetsVerified);
    }
    public static void main(String[] args) throws Exception {
        var boards = BoardRegistry.load(Path.of("catalog/boards.tsv"));
        var stm = SetupCatalog.forBoard(boards.require("stm32f446re-nucleo"), true);
        var esp = SetupCatalog.forBoard(boards.require("esp32s3-devkit"), true);
        var ready = report(true, false, false, Set.of(), true);
        var rustup = SetupReadiness.evaluate(SetupCatalog.require(stm, "install-rustup"), ready);
        ok(rustup.installed() && !rustup.canInstall(), "No WinGet needed with installed Rustup");
        ok(rustup.observation().contains("WinGet not needed"), "Explain optional WinGet");
        var stable = SetupReadiness.evaluate(SetupCatalog.require(stm, "install-stable"), ready);
        ok(stable.installed() && !stable.canInstall(), "No unnecessary compiler reinstall");
        for (String id : new String[] {"install-rust-src", "install-rust-analyzer",
                "install-clippy", "install-rustfmt"}) {
            var result = SetupReadiness.evaluate(SetupCatalog.require(stm, id), ready);
            ok(result.installed() && !result.canInstall(),
                    "Verified component should not offer reinstall: " + id);
        }
        var probe = SetupReadiness.evaluate(SetupCatalog.require(stm, "install-probe-rs"), ready);
        ok(probe.installed() && !probe.canInstall(), "Probe executable verified, no reinstall");
        var manual = SetupReadiness.evaluate(SetupCatalog.require(stm, "stm32-stlink-driver"), ready);
        ok(manual.state() == SetupReadiness.State.MANUAL && !manual.canInstall(),
                "USB driver never auto-installed");
        var notChecked = SetupReadiness.evaluate(SetupCatalog.require(stm, "install-target"), ready);
        ok(notChecked.state() == SetupReadiness.State.UNKNOWN && !notChecked.canInstall(),
                "Never claim missing target when inventory unavailable");
        var targetPresent = report(true, false, true, Set.of("thumbv7em-none-eabihf"), true);
        ok(SetupReadiness.evaluate(SetupCatalog.require(stm, "install-target"), targetPresent)
                .installed(), "Installed target from verified Rustup inventory");
        var targetMissing = report(true, false, true, Set.of("thumbv6m-none-eabi"), true);
        var missing = SetupReadiness.evaluate(SetupCatalog.require(stm, "install-target"), targetMissing);
        ok(missing.state() == SetupReadiness.State.MISSING && missing.canInstall(),
                "Offer exact missing Rust target");
        var noTools = report(false, false, false, Set.of(), false);
        var rustupMissing = SetupReadiness.evaluate(
                SetupCatalog.require(stm, "install-rustup"), noTools);
        ok(rustupMissing.state() == SetupReadiness.State.MISSING && !rustupMissing.canInstall(),
                "Do not try WinGet installer when winget missing");
        var componentUnknown = SetupReadiness.evaluate(
                SetupCatalog.require(stm, "install-clippy"), noTools);
        ok(componentUnknown.state() == SetupReadiness.State.UNKNOWN
                && !componentUnknown.canInstall(), "Unknown inventory is not missing");
        var espflash = SetupReadiness.evaluate(
                SetupCatalog.require(esp, "install-espflash"), ready);
        ok(espflash.state() == SetupReadiness.State.MISSING && espflash.canInstall(),
                "Espflash is optional and installable on explicit Espressif selection");
        var xtensa = SetupReadiness.evaluate(
                SetupCatalog.require(esp, "install-esp-toolchain"), ready);
        ok(xtensa.state() == SetupReadiness.State.UNKNOWN && !xtensa.installed()
                && !xtensa.canInstall(), "Do not assume espup has installed the compiler");
        ok(!SetupReadiness.evaluate(SetupCatalog.require(stm, "install-rustup"), null)
                .canInstall(), "Do not install before host scan");
        System.out.println("PASS setup readiness: " + checks + " checks");
    }
}
