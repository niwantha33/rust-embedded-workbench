// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.core;
import java.util.List;

/** A preview only: does not run programs, build firmware or flash a device. */
public record BuildPlan(Board board, List<String> prerequisites,
        List<String> commands, List<String> warnings) {
    public BuildPlan {
        prerequisites = List.copyOf(prerequisites);
        commands = List.copyOf(commands);
        warnings = List.copyOf(warnings);
    }
    public static BuildPlan forBoard(Board board) {
        if (board.toolchain() == Board.Toolchain.espup) {
            return new BuildPlan(board,
                    List.of("Install Rust, Cargo, espup and a compatible esp-hal project."),
                    List.of("cargo install espup --locked", "espup install",
                            "cargo +esp build --target " + board.rustTarget()),
                    List.of("Preview only: board build is not validated.",
                            "Use a chip-compatible ESP template and linker settings.",
                            "Flashing and debugging are disabled until hardware validation."));
        }
        return new BuildPlan(board,
                List.of("Install Rust/Cargo and create a compatible " + board.framework() + " project."),
                List.of("rustup target add " + board.rustTarget(),
                        "cargo build --target " + board.rustTarget()),
                List.of("Commands require a compatible Cargo project, HAL and linker setup.",
                        "Flashing and debugging are disabled until hardware validation."));
    }
}
