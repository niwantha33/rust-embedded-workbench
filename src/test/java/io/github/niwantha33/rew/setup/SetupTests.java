// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.niwantha33.rew.core.Board;
import io.github.niwantha33.rew.core.BoardRegistry;

public final class SetupTests {
    private static int checks = 0;
    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
    private interface Work { void run() throws Exception; }
    private static void rejects(Class<? extends Exception> type, Work work, String name)
            throws Exception {
        try { work.run(); }
        catch (Exception error) {
            check(type.isInstance(error), name + " (wrong exception: " + error + ")");
            return;
        }
        throw new AssertionError("Unsafe operation accepted: " + name);
    }
    public static void main(String[] args) throws Exception {
        BoardRegistry boards = BoardRegistry.load(Path.of("catalog/boards.tsv"));
        for (Board board : boards.all()) {
            List<SetupCatalog.Step> plan = SetupCatalog.forBoard(board, true);
            check(!plan.isEmpty(), "Missing setup plan " + board.id());
            check(plan.stream().map(SetupCatalog.Step::id).distinct().count() == plan.size(),
                    "Duplicate installer recipe");
            check(plan.stream().anyMatch(s -> s.kind() == SetupCatalog.Kind.DRIVER),
                    "Missing probe/driver instructions " + board.id());
            for (SetupCatalog.Step step : plan) {
                check(step.documentation().getScheme().equals("https"), "Non-HTTPS documentation");
                check(step.mode() == SetupCatalog.Mode.MANAGED
                                || step.command().isEmpty(),
                        "Manual driver operation must not install anything");
                check(step.kind() != SetupCatalog.Kind.DRIVER
                                || step.mode() == SetupCatalog.Mode.MANUAL,
                        "Drivers must never auto-install");
                if (step.mode() == SetupCatalog.Mode.MANAGED) {
                    check(List.of("rustup", "cargo", "winget", "espup").contains(
                            step.command().get(0)), "Untrusted installer executable");
                    check(step.command().stream().noneMatch(s -> s.contains("&&")
                            || s.contains("|") || s.equals("sudo")
                            || s.equals("pnputil") || s.equals("powershell")
                            || s.equals("-ExecutionPolicy")), "Shell/driver substitution");
                }
            }
            if (board.toolchain() == Board.Toolchain.stable) {
                var target = SetupCatalog.require(plan, "install-target");
                check(target.command().equals(List.of("rustup", "target", "add", board.rustTarget())),
                        "Wrong chip target installation");
            } else {
                check(plan.stream().noneMatch(s -> s.id().equals("install-target")),
                        "Xtensa cannot use stable rustup target add");
                check(SetupCatalog.require(plan, "install-esp-toolchain")
                        .command().equals(List.of("espup", "install")), "Missing Espressif toolchain");
            }
        }
        check(SetupCatalog.forBoard(boards.require("mspm0g3507-launchpad"), true)
                        .stream().anyMatch(s -> s.id().equals("ti-xds110")),
                "TI driver setup missing");
        check(SetupCatalog.forBoard(boards.require("stm32f446re-nucleo"), true)
                        .stream().anyMatch(s -> s.id().equals("stm32-stlink-driver")),
                "ST-LINK driver setup missing");
        check(SetupCatalog.forBoard(boards.require("nrf52840-dk"), true)
                        .stream().anyMatch(s -> s.id().equals("nrf-jlink-driver")),
                "SEGGER driver setup missing");

        var captured = new ArrayList<List<String>>();
        AtomicBoolean invoked = new AtomicBoolean();
        var executor = new SetupExecutor((argv, timeout, cancelled) -> {
            invoked.set(true);
            captured.add(List.copyOf(argv));
            check(timeout.toMinutes() == 15, "Missing bounded setup deadline");
            return new SetupExecutor.Result(0, "test: no real installation performed");
        });
        rejects(SecurityException.class, () -> executor.install(
                boards, "stm32f446re-nucleo", "install-target", false, () -> false),
                "Unapproved setup");
        check(!invoked.get(), "Consent denial must not execute a process");
        rejects(SecurityException.class, () -> executor.install(
                boards, "stm32f446re-nucleo", "stm32-stlink-driver", true, () -> false),
                "Cannot auto-install Windows drivers");
        rejects(IllegalArgumentException.class, () -> executor.install(
                boards, "stm32f446re-nucleo", "../evil", true, () -> false),
                "Uncatalogued operation");
        rejects(java.io.InterruptedIOException.class, () -> executor.install(
                boards, "stm32f446re-nucleo", "install-target", true, () -> true),
                "Already cancelled setup");
        check(!invoked.get(), "Rejected requests must not launch processes");
        var result = executor.install(boards, "stm32f446re-nucleo",
                "install-target", true, () -> false);
        check(result.succeeded() && captured.size() == 1, "Explicit managed install dispatched");
        check(captured.get(0).equals(List.of("rustup", "target", "add",
                "thumbv7em-none-eabihf")), "Unmodified fixed command vector");
        rejects(java.util.NoSuchElementException.class, () -> executor.install(
                boards, "unknown-board", "install-target", true, () -> false),
                "Unknown board must fail closed");
        String usb = "USB\\VID_0451&PID_BEF3\\0\n"
                + "USB\\VID_0483&PID_374B\\1\n"
                + "USB\\VID_0451&PID_BEF3\\2\n"
                + "USB\\VID_10C4&PID_EA60\\3\n"
                + "ACPI\\PNP0001\\5\n"
                + "USB\\VID_7777&PID_0001\\4\n";
        var hints = ProbeScanner.interpret(usb);
        check(hints.size() == 3, "Only allowlisted USB vendor identifiers deduplicated");
        check(hints.get(0).vendorId().equals("0451")
                && hints.get(0).productId().equals("BEF3"), "XDS110 candidate parsed");
        check(hints.get(1).vendorId().equals("0483"), "ST-LINK candidate parsed");
        check(hints.get(2).vendorHint().contains("UART"), "USB-UART bridge detected");
        check(ProbeScanner.interpret(null).isEmpty()
                && ProbeScanner.interpret("USB\\VID_INVALID").isEmpty(),
                "Malformed USB evidence rejected");
        System.out.println("PASS setup/driver security: " + checks + " checks");
    }
}
