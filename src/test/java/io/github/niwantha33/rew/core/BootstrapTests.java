// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.core;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BootstrapTests {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        BoardRegistry catalog = BoardRegistry.load(Path.of("catalog/boards.tsv"));
        check(catalog.all().size() == 6, "Expected six candidate boards");
        check(catalog.all().stream().map(Board::vendor).distinct().count() == 5,
                "Expected five MCU vendors");
        for (Board board : catalog.all()) {
            check(board.evidence() == Board.Evidence.DOCS_ONLY, "False hardware verification");
            BuildPlan plan = BuildPlan.forBoard(board);
            check(plan.commands().stream().anyMatch(c -> c.contains(board.rustTarget())),
                    "Missing explicit Rust target");
            check(plan.commands().stream().noneMatch(c -> c.startsWith("probe-rs ")
                    || c.startsWith("openocd ") || c.startsWith("cargo flash")),
                    "Unsafe implicit flash/debug action");
            check(plan.warnings().stream().anyMatch(w -> w.contains("Flashing")),
                    "Missing validation warning");
        }
        check(catalog.require("mspm0g3507-launchpad").rustTarget()
                .equals("thumbv6m-none-eabi"), "Incorrect MSPM0 target");
        check(BuildPlan.forBoard(catalog.require("esp32s3-devkit")).commands()
                .get(0).contains("espup"), "Xtensa toolchain not distinguished");
        check(!ToolDetector.isOnPath("../inject", "/bin", false), "Invalid program accepted");
        check(!ToolDetector.isOnPath("probe-rs", "", false), "Empty PATH accepted");
        Path temporary = Files.createTempFile("invalid-catalog", ".tsv");
        try {
            Files.writeString(temporary, "broken\\trow");
            boolean rejected = false;
            try { BoardRegistry.load(temporary); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid catalog row was accepted");
        } finally { Files.deleteIfExists(temporary); }
        System.out.println("PASS: catalog, evidence gating, plans and tool detection");
    }
}
