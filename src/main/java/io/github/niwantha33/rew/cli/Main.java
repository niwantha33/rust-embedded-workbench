// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cli;

import io.github.niwantha33.rew.core.Board;
import io.github.niwantha33.rew.core.BoardRegistry;
import io.github.niwantha33.rew.core.BuildPlan;
import io.github.niwantha33.rew.core.ToolDetector;
import java.nio.file.Path;

public final class Main {
    private Main() { }
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help")) { usage(); return; }
        if (args[0].equals("doctor")) {
            for (String name : new String[] {"java", "javac", "rustup", "cargo",
                    "rust-analyzer", "probe-rs", "openocd", "espup"}) {
                System.out.printf("%-15s %s%n", name,
                        ToolDetector.isOnCurrentPath(name) ? "on PATH (unverified)" : "not found");
            }
            return;
        }
        BoardRegistry catalog = BoardRegistry.load(Path.of(
                System.getProperty("rew.catalog", "catalog/boards.tsv")));
        switch (args[0]) {
            case "boards" -> {
                for (Board board : catalog.all())
                    System.out.printf("%-25s %-22s %-30s %s%n", board.id(), board.vendor(),
                            board.rustTarget(), board.evidence());
            }
            case "show" -> {
                requireId(args);
                Board b = catalog.require(args[1]);
                System.out.println("Board: " + b.board() + " / " + b.mcu());
                System.out.println("Vendor: " + b.vendor());
                System.out.println("Rust target: " + b.rustTarget());
                System.out.println("Framework candidate: " + b.framework());
                System.out.println("Debug candidate (unverified): " + b.debugCandidate());
                System.out.println("Evidence: " + b.evidence());
                System.out.println("Source: " + b.upstreamSource());
                System.out.println("Notes: " + b.notes());
            }
            case "plan" -> {
                requireId(args);
                BuildPlan plan = BuildPlan.forBoard(catalog.require(args[1]));
                System.out.println("Build plan (PREVIEW ONLY): " + plan.board().id());
                for (String s : plan.prerequisites()) System.out.println("Prerequisite: " + s);
                for (String s : plan.commands()) System.out.println("  $ " + s);
                for (String s : plan.warnings()) System.out.println("WARNING: " + s);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }
    private static void requireId(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Expected one board ID");
    }
    private static void usage() {
        System.out.println("Rust Embedded Workbench (read-only bootstrap)");
        System.out.println("boards | show <board-id> | plan <board-id> | doctor | help");
    }
}
