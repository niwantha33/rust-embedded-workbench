// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class HostToolchainTests {
    private static int checks;
    private static void ok(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static HostToolchain.Report report(boolean rustup, boolean cargo,
            boolean rustc, boolean winget, Set<String> components, boolean verified) {
        Map<String, HostToolchain.Tool> values = new LinkedHashMap<>();
        for (String name : List.of("rustup", "cargo", "rustc", "winget")) {
            boolean available = switch (name) {
                case "rustup" -> rustup;
                case "cargo" -> cargo;
                case "rustc" -> rustc;
                default -> winget;
            };
            values.put(name, new HostToolchain.Tool(name,
                    available ? HostToolchain.State.AVAILABLE : HostToolchain.State.MISSING,
                    null, available ? name + " 1.0 (synthetic)" : ""));
        }
        return new HostToolchain.Report(values, components, verified);
    }
    public static void main(String[] args) throws Exception {
        var empty = report(false, false, false, true, Set.of(), false);
        var all = List.of("install-rustup", "install-stable", "install-rust-src",
                "install-rust-analyzer", "install-clippy", "install-rustfmt");
        ok(HostToolchain.essentials(empty, true).equals(all),
                "WinGet installation then stable Rust + four components");
        ok(HostToolchain.essentials(empty, false).isEmpty(),
                "Non-Windows Rustup bootstrap must remain manual");
        ok(HostToolchain.essentials(report(false, false, false, false, Set.of(), false), true)
                .isEmpty(), "No WinGet means no invented installer");
        ok(HostToolchain.essentials(report(false, true, true, true, Set.of(), false), true)
                .isEmpty(), "Never replace an externally managed usable Rust toolchain");
        var installed = report(true, true, true, true, Set.of(
                "rust-src", "rust-analyzer", "clippy", "rustfmt"), true);
        ok(installed.readyForCargo(), "Cargo readiness needs compiler and Cargo");
        ok(HostToolchain.essentials(installed, true).isEmpty(),
                "Fully installed Rust host must not reinstall anything");
        ok(HostToolchain.essentials(report(true, true, true, true,
                Set.of("rust-src", "clippy", "rustfmt"), true), true)
                .equals(List.of("install-rust-analyzer")),
                "Only missing component is selected");
        ok(HostToolchain.essentials(report(true, false, false, true,
                Set.of(), false), true).contains("install-stable"),
                "Broken rustc/Cargo triggers stable toolchain recovery");
        ok(!report(true, true, false, true, Set.of(), true).readyForCargo(),
                "Rustc must be executable before the build button is enabled");

        var recipes = SetupCatalog.hostEssentials(true);
        ok(recipes.stream().map(SetupCatalog.Step::id).distinct().count() == recipes.size(),
                "Host essential IDs must be distinct");
        ok(recipes.stream().noneMatch(x -> x.kind() == SetupCatalog.Kind.DRIVER),
                "Never install USB/kernel drivers in host essentials");
        for (String id : all) {
            var step = SetupCatalog.require(recipes, id);
            ok(step.mode() == SetupCatalog.Mode.MANAGED, "All Windows host steps are catalogued");
            ok(step.documentation().getScheme().equals("https"),
                    "Host source uses HTTPS");
            ok(step.command().stream().noneMatch(x -> x.contains("&&")
                    || x.equals("sh") || x.equals("powershell") || x.equals("pnputil")),
                    "No arbitrary shell, driver or privilege escalation");
        }
        ok(SetupCatalog.require(recipes, "install-rustup").command().equals(
                List.of("winget", "install", "--id", "Rustlang.Rustup", "--exact",
                        "--source", "winget", "--accept-source-agreements",
                        "--accept-package-agreements")), "Exact official Rustup WinGet ID");
        ok(SetupCatalog.require(recipes, "install-stable").command().equals(
                List.of("rustup", "toolchain", "install", "stable")),
                "Host compiler is installed using rustup");
        ok(SetupCatalog.require(recipes, "install-rust-src").command().equals(
                List.of("rustup", "component", "add", "rust-src")),
                "rust-analyzer source component");

        AtomicInteger called = new AtomicInteger();
        SetupExecutor executor = new SetupExecutor((argv, timeout, cancelled) -> {
            called.incrementAndGet();
            ok(timeout.toMinutes() == 15, "Host installs have a bounded deadline");
            ok(argv.equals(List.of("rustup", "component", "add", "clippy")),
                    "Host installer only executes catalog argv");
            return new SetupExecutor.Result(0, "simulated success");
        });
        try {
            executor.installHost("install-clippy", false, () -> false);
            throw new AssertionError("Missing user approval accepted");
        } catch (SecurityException expected) {
            ok(called.get() == 0, "Consent gate prevents execution");
        }
        try {
            executor.installHost("../install-clippy", true, () -> false);
            throw new AssertionError("Unknown host step accepted");
        } catch (IllegalArgumentException expected) {
            ok(called.get() == 0, "Host recipe allowlist enforced");
        }
        try {
            executor.installHost("install-clippy", true, () -> true);
            throw new AssertionError("Cancelled install accepted");
        } catch (java.io.InterruptedIOException expected) {
            ok(called.get() == 0, "Cancelled setup cannot spawn a process");
        }
        ok(executor.installHost("install-clippy", true, () -> false).succeeded()
                && called.get() == 1, "Approved host recipe runs once");

        Path home = Files.createTempDirectory("rew-rust-home-");
        try {
            Path bin = home.resolve(".cargo").resolve("bin");
            Files.createDirectories(bin);
            boolean windows = SetupExecutor.isWindows();
            Path tool = bin.resolve(windows ? "rustup.exe" : "rustup");
            Files.writeString(tool, "synthetic executable");
            tool.toFile().setExecutable(true);
            if (Files.isExecutable(tool)) {
                ok(HostToolchain.locate("rustup", "", home, windows)
                        .equals(tool.toAbsolutePath().normalize()),
                        "Find newly installed Rustup without restarting Eclipse");
            }
            try {
                HostToolchain.locate("../sh", "", home, windows);
                throw new AssertionError("Arbitrary executable lookup accepted");
            } catch (IllegalArgumentException expected) {
                ok(true, "Host discovery has an executable allowlist");
            }
        } finally {
            try (var files = Files.walk(home)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(file);
            }
        }
        System.out.println("PASS Rust host setup: " + checks + " checks");
    }
}
