// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cargo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.niwantha33.rew.core.Board;
import io.github.niwantha33.rew.core.BoardRegistry;

/** No shell or arbitrary extra arguments; target names come from the board catalog. */
public record CargoPlan(CargoProject project, Operation operation, String boardId,
                        String rustTarget, boolean release, boolean offline,
                        boolean locked, List<String> command) {
    public enum Operation { METADATA, CHECK, BUILD, CLIPPY, TEST_HOST, CLEAN }
    public CargoPlan {
        command = List.copyOf(command);
    }

    public static CargoPlan create(CargoProject project, Operation operation,
            BoardRegistry registry, String boardId, boolean release,
            boolean offline) throws IOException {
        if (project == null || operation == null || registry == null)
            throw new IOException("A valid project, operation and board registry are required.");
        // Recheck the manifest every run rather than trusting a stale UI selection.
        project = CargoProject.open(project.manifest());
        String chosen = boardId == null ? "" : boardId.trim();
        String target = "";
        if (!chosen.isEmpty()) {
            Board board = registry.require(chosen);
            target = board.rustTarget();
        }
        if (operation == Operation.METADATA || operation == Operation.TEST_HOST) {
            target = "";
        }
        List<String> argv = new ArrayList<>();
        argv.add("cargo");
        switch (operation) {
            case METADATA -> {
                argv.addAll(List.of("metadata", "--format-version", "1", "--no-deps"));
            }
            case CHECK -> argv.add("check");
            case BUILD -> argv.add("build");
            case CLIPPY -> argv.add("clippy");
            case TEST_HOST -> argv.add("test");
            case CLEAN -> argv.add("clean");
        }
        argv.addAll(List.of("--manifest-path", project.manifest().toString()));
        if (operation != Operation.CLEAN && operation != Operation.METADATA)
            argv.addAll(List.of("--color", "never", "--message-format", "short"));
        if (!target.isBlank())
            argv.addAll(List.of("--target", target));
        if (release && operation != Operation.METADATA)
            argv.add("--release");
        if (offline && operation != Operation.CLEAN) argv.add("--offline");
        boolean hasLockfile = project.hasLockfile();
        // Lockfile is preserved where present; Cargo may create one on first build.
        if (hasLockfile && operation != Operation.CLEAN) argv.add("--locked");
        return new CargoPlan(project, operation, chosen, target, release,
                offline, hasLockfile, argv);
    }

    public boolean mayExecuteProjectCode() {
        return operation == Operation.CHECK || operation == Operation.BUILD
                || operation == Operation.CLIPPY || operation == Operation.TEST_HOST;
    }

    public String safetyNotice() {
        String note = "Cargo operates in " + project.directory()
                + ". Cargo dependencies/build scripts may execute third-party code "
                + "with your user permissions and produce target/ artifacts.";
        if (!project.hasLockfile()) note += " No Cargo.lock exists; Cargo may generate one.";
        if (operation == Operation.CLEAN)
            note += " CLEAN removes Cargo-generated build files.";
        if (operation == Operation.TEST_HOST)
            note += " TEST_HOST runs tests on this computer, never on the MCU.";
        if (!rustTarget.isEmpty())
            note += " Target is a catalog candidate, not verified build or hardware support.";
        if (offline) note += " Network fetching is disabled by --offline.";
        return note;
    }
}
